import { PhotoEditParams } from '../types';

/**
 * Conditional rule inferred by the VLM once per trip during reference calibration.
 * Maps detected lighting conditions to concrete adjustments without per-photo API calls.
 */
export interface ConditionalRule {
  condition:
    | 'underexposed_or_backlit'
    | 'indoor_warm_light'
    | 'overexposed_or_bright_daylight'
    | 'cool_overcast'
    | 'neutral_balanced';
  label?: string;
  exposureShift?: number;
  whiteBalanceShiftK?: number;
  saturationShift?: number;
  shadowsLift?: number;
  contrastShift?: number;
  cropLeft?: number;
  cropTop?: number;
  cropRight?: number;
  cropBottom?: number;
  blurBackground?: boolean;
  reasoning?: string;
}

/**
 * Result of the local, offline image analysis.
 * Computed entirely on-device via Canvas pixel heuristics without calling any AI model.
 */
export interface LightingAnalysis {
  averageLuminance: number;       // 0..1 (0 = pure black, 1 = pure white)
  redToBlueRatio: number;         // > 1.2 = warm/incandescent, < 0.88 = cool/overcast
  shadowPixelFraction: number;    // proportion of deep shadow pixels (< 64/255)
  highlightPixelFraction: number; // proportion of near-blown highlight pixels (> 215/255)
  avgR: number;
  avgG: number;
  avgB: number;
  condition:
    | 'underexposed_or_backlit'
    | 'indoor_warm_light'
    | 'overexposed_or_bright_daylight'
    | 'cool_overcast'
    | 'neutral_balanced';
  reason: string;
}

/**
 * Executes PhotoEditParams on pixels via HTML Canvas, mirroring the logic in ImageProcessor.kt:
 * - Local, offline lighting classifier & rule-matching engine
 * - Exposure scaling (brightness multiply)
 * - Contrast shift with center pivot
 * - Saturation adjustment (RGB to grayscale delta)
 * - White balance shift (Kelvin-like warmth tint: red/blue channel adaptation)
 * - Shadow lift (per-pixel tonal curve lifting shadow tones while preserving highlights)
 * - Normalized box crop
 * - Optional depth blur / vignette
 */
export class ImageProcessor {
  /**
   * LOCAL, OFFLINE CLASSIFIER:
   * Analyzes an image's basic lighting condition completely on-device without any AI API call.
   * Renders the image to a low-res 64x64 offscreen canvas to sample luminance and RGB distributions in < 3ms.
   */
  public static async classifyLighting(imageSource: string | HTMLImageElement): Promise<LightingAnalysis> {
    return new Promise((resolve) => {
      const img = typeof imageSource === 'string' ? new Image() : imageSource;
      if (typeof imageSource === 'string') {
        img.crossOrigin = 'anonymous';
      }

      const runAnalysis = () => {
        const sampleSize = 64; // 64x64 grid is fast and statistically sufficient for scene lighting
        const canvas = document.createElement('canvas');
        canvas.width = sampleSize;
        canvas.height = sampleSize;
        const ctx = canvas.getContext('2d', { willReadFrequently: true });

        if (!ctx) {
          resolve({
            averageLuminance: 0.5,
            redToBlueRatio: 1.0,
            shadowPixelFraction: 0,
            highlightPixelFraction: 0,
            avgR: 128,
            avgG: 128,
            avgB: 128,
            condition: 'neutral_balanced',
            reason: 'Default neutral analysis fallback',
          });
          return;
        }

        ctx.drawImage(img, 0, 0, sampleSize, sampleSize);
        const imgData = ctx.getImageData(0, 0, sampleSize, sampleSize);
        const data = imgData.data;

        let totalLum = 0;
        let totalR = 0;
        let totalG = 0;
        let totalB = 0;
        let shadowCount = 0;
        let highlightCount = 0;
        const totalPixels = sampleSize * sampleSize;

        for (let i = 0; i < data.length; i += 4) {
          const r = data[i];
          const g = data[i + 1];
          const b = data[i + 2];

          // Standard ITU-R BT.709 relative luminance formula
          const lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
          totalLum += lum;
          totalR += r;
          totalG += g;
          totalB += b;

          // Shadow threshold: luminance < 64 (dark underexposed zones)
          if (lum < 64) shadowCount++;
          // Highlight threshold: luminance > 215 (bright daylight / sky)
          if (lum > 215) highlightCount++;
        }

        const avgLumNorm = Math.max(0, Math.min(1, totalLum / totalPixels / 255));
        const avgR = totalR / totalPixels;
        const avgG = totalG / totalPixels;
        const avgB = totalB / totalPixels;
        const redToBlueRatio = avgR / Math.max(1, avgB);
        const shadowPixelFraction = shadowCount / totalPixels;
        const highlightPixelFraction = highlightCount / totalPixels;

        // RULE MATCHING DECISION TREE (Local Offline Heuristics)
        let condition: LightingAnalysis['condition'] = 'neutral_balanced';
        let reason = '';

        // 1. Backlit or Underexposed:
        // Either overall luminance is low (< 0.36) or heavy shadow concentration (> 38% deep shadows)
        if (avgLumNorm < 0.36 || (shadowPixelFraction > 0.38 && avgLumNorm < 0.55)) {
          condition = 'underexposed_or_backlit';
          reason = `Low overall luminance (${Math.round(avgLumNorm * 100)}%) with ${Math.round(shadowPixelFraction * 100)}% shadow density`;
        }
        // 2. Overexposed or Harsh Daylight:
        // Overall luminance is high (> 0.68) or strong specular highlights/blown sky (> 28%)
        else if (avgLumNorm > 0.68 || highlightPixelFraction > 0.28) {
          condition = 'overexposed_or_bright_daylight';
          reason = `High overall luminance (${Math.round(avgLumNorm * 100)}%) with ${Math.round(highlightPixelFraction * 100)}% highlight density`;
        }
        // 3. Indoor Warm / Incandescent Light:
        // Strong warm cast (red/blue > 1.20) in moderate indoor light levels
        else if (redToBlueRatio > 1.20 && avgLumNorm < 0.65) {
          condition = 'indoor_warm_light';
          reason = `Warm indoor illumination (R/B ratio ${redToBlueRatio.toFixed(2)}, luminance ${Math.round(avgLumNorm * 100)}%)`;
        }
        // 4. Cool Overcast Sky:
        // Blue/cyan dominant (red/blue < 0.88) characteristic of overcast or shaded daylight
        else if (redToBlueRatio < 0.88) {
          condition = 'cool_overcast';
          reason = `Cool overcast daylight cast (R/B ratio ${redToBlueRatio.toFixed(2)})`;
        }
        // 5. Neutral Balanced:
        // Normal daylight without severe exposure or color temperature extremes
        else {
          condition = 'neutral_balanced';
          reason = `Balanced ambient scene (luminance ${Math.round(avgLumNorm * 100)}%, R/B ratio ${redToBlueRatio.toFixed(2)})`;
        }

        resolve({
          averageLuminance: avgLumNorm,
          redToBlueRatio,
          shadowPixelFraction,
          highlightPixelFraction,
          avgR,
          avgG,
          avgB,
          condition,
          reason,
        });
      };

      if (typeof imageSource === 'string') {
        img.onload = runAnalysis;
        img.onerror = () => {
          resolve({
            averageLuminance: 0.5,
            redToBlueRatio: 1.0,
            shadowPixelFraction: 0,
            highlightPixelFraction: 0,
            avgR: 128,
            avgG: 128,
            avgB: 128,
            condition: 'neutral_balanced',
            reason: 'Image load fallback',
          });
        };
        img.src = imageSource;
      } else {
        if (img.complete && img.naturalWidth !== 0) {
          runAnalysis();
        } else {
          img.onload = runAnalysis;
        }
      }
    });
  }

  /**
   * RULE MATCHER:
   * Maps the local offline lighting analysis to the conditional rules returned by the model during calibration.
   * If a matching condition rule exists, its parameters are applied; otherwise it defaults to neutral/reference values.
   * ZERO AI model calls are made here.
   */
  public static matchAndApplyRules(
    photoId: string | number,
    lighting: LightingAnalysis,
    rules: ConditionalRule[],
    fallbackParams: PhotoEditParams
  ): PhotoEditParams {
    // Find the specific rule matching this photo's lighting condition
    const matchedRule =
      rules.find((r) => r.condition === lighting.condition) ||
      rules.find((r) => r.condition === 'neutral_balanced');

    if (matchedRule) {
      return {
        photoId,
        exposureShift: Number(matchedRule.exposureShift ?? fallbackParams.exposureShift ?? 0),
        whiteBalanceShiftK: Number(matchedRule.whiteBalanceShiftK ?? fallbackParams.whiteBalanceShiftK ?? 0),
        saturationShift: Number(matchedRule.saturationShift ?? fallbackParams.saturationShift ?? 0),
        shadowsLift: Number(matchedRule.shadowsLift ?? fallbackParams.shadowsLift ?? 0),
        contrastShift: Number(matchedRule.contrastShift ?? fallbackParams.contrastShift ?? 0),
        cropLeft: Number(matchedRule.cropLeft ?? fallbackParams.cropLeft ?? 0),
        cropTop: Number(matchedRule.cropTop ?? fallbackParams.cropTop ?? 0),
        cropRight: Number(matchedRule.cropRight ?? fallbackParams.cropRight ?? 1),
        cropBottom: Number(matchedRule.cropBottom ?? fallbackParams.cropBottom ?? 1),
        blurBackground: Boolean(matchedRule.blurBackground ?? fallbackParams.blurBackground ?? false),
        reasoning: `[Offline Rule: ${lighting.condition}] ${lighting.reason}. Action: ${
          matchedRule.reasoning || 'Applied inferred calibration rule'
        }`,
      };
    }

    // Default to calibrated reference parameters if no rule matched
    return {
      ...fallbackParams,
      photoId,
      reasoning: `[Offline Local Analysis: ${lighting.condition}] ${lighting.reason}. Applied baseline reference calibration.`,
    };
  }

  /**
   * Applies the parameters to a source image onto a destination canvas.
   */
  public static apply(
    source: HTMLImageElement | HTMLCanvasElement,
    params: PhotoEditParams,
    destCanvas: HTMLCanvasElement
  ): void {
    const sw = source.width;
    const sh = source.height;

    if (!sw || !sh) return;

    // 1. Calculate Crop Box
    const left = Math.max(0, Math.min(sw - 1, Math.floor((params.cropLeft ?? 0) * sw)));
    const top = Math.max(0, Math.min(sh - 1, Math.floor((params.cropTop ?? 0) * sh)));
    const right = Math.max(left + 1, Math.min(sw, Math.ceil((params.cropRight ?? 1) * sw)));
    const bottom = Math.max(top + 1, Math.min(sh, Math.ceil((params.cropBottom ?? 1) * sh)));

    const cropW = Math.max(1, right - left);
    const cropH = Math.max(1, bottom - top);

    destCanvas.width = cropW;
    destCanvas.height = cropH;

    const ctx = destCanvas.getContext('2d', { willReadFrequently: true });
    if (!ctx) return;

    // Draw the cropped region to the canvas
    ctx.drawImage(source, left, top, cropW, cropH, 0, 0, cropW, cropH);

    const imgData = ctx.getImageData(0, 0, cropW, cropH);
    const data = imgData.data;

    // Parameters
    const exposureScale = 1 + (params.exposureShift || 0); // e.g. 0.2 -> 1.2
    const contrast = 1 + (params.contrastShift || 0);     // e.g. 0.1 -> 1.1
    const contrastOffset = (1 - contrast) * 128;
    const saturation = 1 + (params.saturationShift || 0); // e.g. 0.15 -> 1.15
    const wbShift = (params.whiteBalanceShiftK || 0) / 2000; // e.g. +400K -> +0.2 warmth
    const shadowsLift = Math.max(0, Math.min(1, params.shadowsLift || 0));

    const rShift = 1 + wbShift;
    const bShift = 1 - wbShift;

    const len = data.length;
    for (let i = 0; i < len; i += 4) {
      let r = data[i];
      let g = data[i + 1];
      let b = data[i + 2];

      // 1. Exposure shift
      r = r * exposureScale;
      g = g * exposureScale;
      b = b * exposureScale;

      // 2. Contrast adjustment centered at 128
      r = r * contrast + contrastOffset;
      g = g * contrast + contrastOffset;
      b = b * contrast + contrastOffset;

      // 3. White balance warmth shift (warmer = more red, less blue)
      r = r * rShift;
      b = b * bShift;

      // 4. Saturation adjustment
      // Luminance weights (Rec. 709)
      const luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
      r = luma + (r - luma) * saturation;
      g = luma + (g - luma) * saturation;
      b = luma + (b - luma) * saturation;

      // 5. Shadows Lift (smooth tone curve lifting values < 128 without blowing out highlights)
      if (shadowsLift > 0.001) {
        const shadowFactor = Math.max(0, 1 - luma / 255);
        const boost = shadowFactor * shadowsLift * 64;
        r += boost;
        g += boost;
        b += boost;
      }

      // Clamp to [0, 255]
      data[i] = r < 0 ? 0 : r > 255 ? 255 : r;
      data[i + 1] = g < 0 ? 0 : g > 255 ? 255 : g;
      data[i + 2] = b < 0 ? 0 : b > 255 ? 255 : b;
    }

    ctx.putImageData(imgData, 0, 0);

    // Optional background blur / soft portrait edge vignette if requested
    if (params.blurBackground) {
      ctx.save();
      const grad = ctx.createRadialGradient(
        cropW / 2, cropH / 2, Math.min(cropW, cropH) * 0.35,
        cropW / 2, cropH / 2, Math.max(cropW, cropH) * 0.75
      );
      grad.addColorStop(0, 'rgba(0,0,0,0)');
      grad.addColorStop(1, 'rgba(0,0,0,0.25)');
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, cropW, cropH);
      ctx.restore();
    }
  }

  /**
   * Renders an image URL with params into a data URL.
   */
  public static async renderToDataUrl(
    imgSrc: string,
    params: PhotoEditParams
  ): Promise<string> {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.onload = () => {
        const canvas = document.createElement('canvas');
        ImageProcessor.apply(img, params, canvas);
        resolve(canvas.toDataURL('image/jpeg', 0.92));
      };
      img.onerror = (e) => reject(e);
      img.src = imgSrc;
    });
  }
}
