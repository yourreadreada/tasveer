import React, { useState, useEffect, useRef } from 'react';
import {
  ArrowLeft,
  Sparkles,
  RefreshCw,
  Eye,
  Sliders,
  Crop,
  Sun,
  Thermometer,
  Palette,
  Contrast,
  Wand2,
} from 'lucide-react';
import { Photo, PhotoEditParams } from '../types';
import { ImageProcessor, LightingAnalysis } from '../utils/imageProcessor';

interface EditScreenProps {
  referencePhotos: Photo[];
  onBack: () => void;
  onConfirmLook: (params: PhotoEditParams, activePhoto: Photo) => void;
  isAnalyzing?: boolean;
}

export const EditScreen: React.FC<EditScreenProps> = ({
  referencePhotos,
  onBack,
  onConfirmLook,
  isAnalyzing = false,
}) => {
  const [activePhotoIndex, setActivePhotoIndex] = useState(0);
  const activePhoto = referencePhotos[activePhotoIndex] || referencePhotos[0];

  // Sliders matching EditScreen.kt
  const [exposure, setExposure] = useState<number>(0.12);
  const [warmth, setWarmth] = useState<number>(0.18); // kelvin shift = warmth * 2000
  const [saturation, setSaturation] = useState<number>(0.15);
  const [contrast, setContrast] = useState<number>(0.1);
  const [shadowsLift, setShadowsLift] = useState<number>(0.15);
  const [cropInset, setCropInset] = useState<number>(0); // 0..0.25 inset for crop
  const [showOriginal, setShowOriginal] = useState<boolean>(false);

  // Local on-device lighting analysis for adaptive presets
  const [lighting, setLighting] = useState<LightingAnalysis | null>(null);
  const [activePreset, setActivePreset] = useState<string | null>(null);
  const [presetFeedback, setPresetFeedback] = useState<string | null>(null);

  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const imgRef = useRef<HTMLImageElement | null>(null);

  // Analyze active photo lighting offline whenever it changes
  useEffect(() => {
    if (!activePhoto) return;
    ImageProcessor.classifyLighting(activePhoto.uri).then((analysis) => {
      setLighting(analysis);
    });
  }, [activePhoto]);

  // Current parameters
  const currentParams: PhotoEditParams = {
    photoId: activePhoto.id,
    exposureShift: exposure,
    whiteBalanceShiftK: Math.round(warmth * 2000),
    saturationShift: saturation,
    contrastShift: contrast,
    shadowsLift: shadowsLift,
    cropLeft: cropInset,
    cropTop: cropInset,
    cropRight: 1 - cropInset,
    cropBottom: 1 - cropInset,
    blurBackground: false,
    reasoning: activePreset
      ? `Calibrated starting from adaptive ${activePreset} filter`
      : 'Calibrated reference style for the trip',
  };

  // Re-render canvas preview whenever parameters or active photo change
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !activePhoto) return;

    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.src = activePhoto.uri;
    imgRef.current = img;

    img.onload = () => {
      if (showOriginal) {
        // Draw unadjusted image
        canvas.width = img.naturalWidth || 800;
        canvas.height = img.naturalHeight || 600;
        const ctx = canvas.getContext('2d');
        if (ctx) ctx.drawImage(img, 0, 0);
      } else {
        // Apply parameters through ImageProcessor
        ImageProcessor.apply(img, currentParams, canvas);
      }
    };
  }, [activePhoto, exposure, warmth, saturation, contrast, shadowsLift, cropInset, showOriginal]);

  const handleReset = () => {
    setExposure(0);
    setWarmth(0);
    setSaturation(0);
    setContrast(0);
    setShadowsLift(0);
    setCropInset(0);
    setActivePreset(null);
    setPresetFeedback('Reset all adjustments to zero baseline.');
  };

  /**
   * ADAPTIVE QUICK STARTING POINT FILTERS
   * Adjusts sliders dynamically according to THIS specific photo's actual lighting,
   * brightness histogram, and color temperature rather than static hardcoded numbers.
   */
  const applyAdaptivePreset = (presetName: string) => {
    setActivePreset(presetName);

    // Live measurements from offline pixel analysis
    const luma = lighting?.averageLuminance ?? 0.5; // 0..1 normalized luminance
    const rOverB = lighting?.redToBlueRatio ?? 1.0;  // > 1.2 = warm cast, < 0.88 = cool cast
    const shadows = lighting?.shadowPixelFraction ?? 0.15;
    const highlights = lighting?.highlightPixelFraction ?? 0.15;

    let newExp = 0;
    let newWarmth = 0;
    let newSat = 0;
    let newContrast = 0;
    let newShadowsLift = 0;
    let feedback = '';

    if (presetName === 'auto') {
      // 1. SMART AUTO-BALANCE:
      // Brings the photo into natural optimal tonal and color harmony
      if (luma < 0.36) {
        newExp = 0.22;
        newShadowsLift = 0.36;
        feedback = 'Lifted dark exposure (+0.22) and opened deep shadows (+0.36).';
      } else if (luma < 0.48) {
        newExp = 0.12;
        newShadowsLift = 0.22;
        feedback = 'Gentle exposure boost (+0.12) with shadow recovery (+0.22).';
      } else if (luma > 0.68 || highlights > 0.25) {
        newExp = -0.12;
        newShadowsLift = 0.08;
        feedback = 'Protected bright highlights (-0.12 exp) to prevent clipping.';
      } else {
        newExp = 0.05;
        newShadowsLift = 0.15;
        feedback = 'Balanced ambient exposure with clean shadow lift (+0.15).';
      }

      // Neutralize extreme color casts
      if (rOverB > 1.25) {
        newWarmth = -0.12; // cooling down warm indoor cast
        newSat = 0.05;
      } else if (rOverB < 0.88) {
        newWarmth = 0.15;  // warming up cold overcast cast
        newSat = 0.10;
      } else {
        newWarmth = 0.04;
        newSat = 0.08;
      }

      newContrast = luma < 0.35 || luma > 0.68 ? 0.06 : 0.10;
    } else if (presetName === 'golden') {
      // 2. WARM GOLDEN HOUR (ADAPTIVE):
      // Rich sun-drenched amber glow, but intelligently prevents over-saturating or blowing out warm/bright photos
      if (rOverB > 1.22) {
        // Photo is ALREADY warm (indoor tungsten or sunset): don't double down with excessive warmth
        newWarmth = 0.12;
        newSat = 0.06; // restrained saturation so skin tones remain natural
      } else if (rOverB < 0.90) {
        // Cool scene: inject generous golden warmth
        newWarmth = 0.36;
        newSat = 0.18;
      } else {
        newWarmth = 0.24;
        newSat = 0.14;
      }

      if (luma > 0.65 || highlights > 0.25) {
        newExp = -0.04; // avoid blown highlights in golden skies
        newShadowsLift = 0.14;
        newContrast = 0.12;
        feedback = 'Applied golden tones with highlight compression to protect sky gradients.';
      } else if (luma < 0.38 || shadows > 0.35) {
        newExp = 0.18;
        newShadowsLift = 0.34;
        newContrast = 0.08;
        feedback = 'Lifted deep shadows (+0.34) and injected warm golden-hour grading.';
      } else {
        newExp = 0.10;
        newShadowsLift = 0.20;
        newContrast = 0.10;
        feedback = 'Applied warm golden hour glow with natural amber midtones.';
      }
    } else if (presetName === 'cinematic') {
      // 3. CINEMATIC MOODY (ADAPTIVE):
      // Sophisticated filmic color contrast and cyan-leaning highlights, but NEVER crushing already dark shadows
      if (luma < 0.38 || shadows > 0.35) {
        // Photo is dark/backlit: lift shadows and keep exposure neutral so subject isn't lost in murky black
        newExp = 0.06;
        newShadowsLift = 0.26;
        newContrast = 0.14;
        feedback = 'Filmic grade: lifted shadows (+0.26) to prevent dark zones from crushing.';
      } else if (luma > 0.65) {
        newExp = -0.14;
        newShadowsLift = 0.06;
        newContrast = 0.22;
        feedback = 'Deepened exposure (-0.14) and boosted contrast (+0.22) for dramatic mood.';
      } else {
        newExp = -0.06;
        newShadowsLift = 0.10;
        newContrast = 0.18;
        feedback = 'Cinematic film contrast (+0.18) with cool cyan undertones.';
      }

      newWarmth = rOverB < 0.90 ? -0.05 : -0.14;
      newSat = -0.08; // slightly desaturated vintage film chrominance
    } else if (presetName === 'airy') {
      // 4. AIRY CLEAN (ADAPTIVE):
      // Luminous, high-key, soft editorial look with gentle midtones and open shadows
      if (luma > 0.65 || highlights > 0.25) {
        // Already bright: gentle touch so highlights don't blow out into pure white blocks
        newExp = 0.06;
        newShadowsLift = 0.16;
        newContrast = -0.04;
        feedback = 'Airy aesthetic: highlights protected on already luminous scene.';
      } else if (luma < 0.40) {
        newExp = 0.28;
        newShadowsLift = 0.42;
        newContrast = -0.08;
        feedback = 'Luminous high-key: opened underexposed shadows (+0.42) with airy exposure.';
      } else {
        newExp = 0.18;
        newShadowsLift = 0.28;
        newContrast = -0.06;
        feedback = 'Soft, bright editorial presentation with creamy midtones.';
      }

      newWarmth = rOverB > 1.20 ? -0.08 : 0.04;
      newSat = 0.06;
    } else if (presetName === 'vibrant') {
      // 5. VIBRANT POP (ADAPTIVE):
      // Rich punchy colors and crisp clarity tailored to landscapes and architecture
      if (luma < 0.40) {
        newExp = 0.14;
        newShadowsLift = 0.26;
      } else if (luma > 0.65) {
        newExp = -0.06;
        newShadowsLift = 0.10;
      } else {
        newExp = 0.06;
        newShadowsLift = 0.15;
      }

      newContrast = 0.16;
      newSat = rOverB > 1.25 ? 0.12 : 0.22; // don't over-saturate if already heavily tinted
      newWarmth = rOverB < 0.90 ? 0.10 : 0.02;
      feedback = 'Punchy color separation and micro-contrast adapted to scene lighting.';
    }

    // Set precise rounded values
    setExposure(Number(newExp.toFixed(2)));
    setWarmth(Number(newWarmth.toFixed(2)));
    setSaturation(Number(newSat.toFixed(2)));
    setContrast(Number(newContrast.toFixed(2)));
    setShadowsLift(Number(newShadowsLift.toFixed(2)));
    setPresetFeedback(feedback);
  };

  const getConditionLabel = (condition?: string) => {
    switch (condition) {
      case 'underexposed_or_backlit':
        return 'Backlit / Low-Light';
      case 'overexposed_or_bright_daylight':
        return 'Bright Daylight / High-Key';
      case 'indoor_warm_light':
        return 'Warm Indoor / Tungsten';
      case 'cool_overcast':
        return 'Cool Overcast / Shade';
      default:
        return 'Balanced Ambient';
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="p-2 rounded-xl bg-stone-900 hover:bg-stone-800 text-stone-300 border border-stone-800 transition-colors"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h1 className="text-xl sm:text-2xl font-bold text-stone-100">
              Calibrate Reference Look
            </h1>
            <p className="text-xs text-stone-400">
              Fine-tune this photo. Quick starting points adapt intelligently to its lighting histogram.
            </p>
          </div>
        </div>

        {/* If multiple reference photos were chosen */}
        {referencePhotos.length > 1 && (
          <div className="flex items-center gap-2 bg-stone-900 p-1 rounded-xl border border-stone-800 self-start sm:self-auto">
            {referencePhotos.map((p, idx) => (
              <button
                key={p.id}
                onClick={() => {
                  setActivePhotoIndex(idx);
                  setActivePreset(null);
                  setPresetFeedback(null);
                }}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                  activePhotoIndex === idx
                    ? 'bg-amber-500 text-stone-950 font-semibold'
                    : 'text-stone-400 hover:text-stone-200'
                }`}
              >
                Photo #{idx + 1}
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Left Column: Live Canvas Viewport */}
        <div className="lg:col-span-7 flex flex-col gap-4">
          <div className="relative rounded-2xl overflow-hidden bg-stone-950 border border-stone-800 shadow-2xl flex items-center justify-center min-h-[380px] max-h-[560px]">
            <canvas
              ref={canvasRef}
              className="max-h-[540px] max-w-full object-contain transition-all"
            />

            {/* Compare overlay button */}
            <div className="absolute top-4 right-4 flex items-center gap-2">
              <button
                onMouseDown={() => setShowOriginal(true)}
                onMouseUp={() => setShowOriginal(false)}
                onTouchStart={() => setShowOriginal(true)}
                onTouchEnd={() => setShowOriginal(false)}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold backdrop-blur-md transition-all flex items-center gap-1.5 select-none ${
                  showOriginal
                    ? 'bg-amber-500 text-stone-950 shadow-lg'
                    : 'bg-stone-900/80 text-stone-200 border border-white/10 hover:bg-stone-800'
                }`}
              >
                <Eye className="w-3.5 h-3.5" />
                <span>{showOriginal ? 'Showing Original' : 'Hold to Compare'}</span>
              </button>
            </div>

            {/* Photo info badge */}
            <div className="absolute bottom-4 left-4 px-3 py-1.5 rounded-lg bg-stone-950/80 backdrop-blur-md border border-stone-800 text-xs text-stone-300 flex items-center gap-2">
              <span>{activePhoto.title || 'Reference photo'}</span>
              {lighting && (
                <span className="text-[10px] text-amber-400 font-mono border-l border-stone-700 pl-2">
                  {getConditionLabel(lighting.condition)} ({Math.round(lighting.averageLuminance * 100)}% lum)
                </span>
              )}
            </div>
          </div>

          {/* Quick Adaptive Presets Bar */}
          <div className="p-3.5 rounded-2xl bg-stone-900/90 border border-stone-800 flex flex-col gap-2.5">
            <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
              <div className="flex items-center gap-1.5 text-stone-300 font-medium">
                <Wand2 className="w-3.5 h-3.5 text-amber-400" />
                <span>Adaptive Starting Points:</span>
                {lighting && (
                  <span className="text-[11px] text-stone-400 font-normal hidden sm:inline">
                    (Auto-calculated for {getConditionLabel(lighting.condition).toLowerCase()})
                  </span>
                )}
              </div>

              <div className="flex flex-wrap gap-1.5">
                <button
                  onClick={() => applyAdaptivePreset('auto')}
                  className={`px-2.5 py-1 rounded-lg text-xs font-medium transition-all ${
                    activePreset === 'auto'
                      ? 'bg-amber-500 text-stone-950 font-bold shadow'
                      : 'bg-stone-800 hover:bg-stone-700 text-amber-300'
                  }`}
                  title="Automatically balances exposure, shadow detail, and color cast"
                >
                  ⚡ Auto Balance
                </button>
                <button
                  onClick={() => applyAdaptivePreset('golden')}
                  className={`px-2.5 py-1 rounded-lg text-xs font-medium transition-all ${
                    activePreset === 'golden'
                      ? 'bg-amber-500 text-stone-950 font-bold shadow'
                      : 'bg-stone-800 hover:bg-stone-700 text-orange-300'
                  }`}
                  title="Warm golden-hour grading adapted to current warmth and highlights"
                >
                  Golden Hour
                </button>
                <button
                  onClick={() => applyAdaptivePreset('cinematic')}
                  className={`px-2.5 py-1 rounded-lg text-xs font-medium transition-all ${
                    activePreset === 'cinematic'
                      ? 'bg-amber-500 text-stone-950 font-bold shadow'
                      : 'bg-stone-800 hover:bg-stone-700 text-cyan-300'
                  }`}
                  title="Filmic contrast and teal undertones without crushing dark shadows"
                >
                  Cinematic
                </button>
                <button
                  onClick={() => applyAdaptivePreset('airy')}
                  className={`px-2.5 py-1 rounded-lg text-xs font-medium transition-all ${
                    activePreset === 'airy'
                      ? 'bg-amber-500 text-stone-950 font-bold shadow'
                      : 'bg-stone-800 hover:bg-stone-700 text-stone-200'
                  }`}
                  title="Luminous high-key look preventing highlight clipping"
                >
                  Airy Clean
                </button>
                <button
                  onClick={() => applyAdaptivePreset('vibrant')}
                  className={`px-2.5 py-1 rounded-lg text-xs font-medium transition-all ${
                    activePreset === 'vibrant'
                      ? 'bg-amber-500 text-stone-950 font-bold shadow'
                      : 'bg-stone-800 hover:bg-stone-700 text-emerald-300'
                  }`}
                  title="Punchy landscape color separation and crisp contrast"
                >
                  Vibrant Pop
                </button>
                <button
                  onClick={handleReset}
                  className="px-2.5 py-1 rounded-lg bg-stone-800 hover:bg-stone-700 text-stone-400 hover:text-stone-200 transition-colors flex items-center gap-1 text-xs"
                  title="Reset all adjustments to zero baseline"
                >
                  <RefreshCw className="w-3 h-3" />
                  <span>Reset</span>
                </button>
              </div>
            </div>

            {/* Preset Adaptive Feedback Note */}
            {presetFeedback && (
              <div className="text-[11px] text-amber-300/90 bg-amber-500/10 px-2.5 py-1 rounded-md border border-amber-500/20 font-sans">
                {presetFeedback}
              </div>
            )}
          </div>
        </div>

        {/* Right Column: Precise Adjustment Sliders (matching EditScreen.kt) */}
        <div className="lg:col-span-5 bg-stone-900/90 rounded-2xl p-6 border border-stone-800 flex flex-col gap-6 shadow-xl">
          <div className="flex items-center justify-between border-b border-stone-800 pb-3">
            <div className="flex items-center gap-2">
              <Sliders className="w-4 h-4 text-amber-400" />
              <h2 className="text-base font-bold text-stone-100">Manual Calibration</h2>
            </div>
            <span className="text-xs text-stone-400 font-mono">
              {activePreset ? `Filter: ${activePreset}` : 'Custom Sliders'}
            </span>
          </div>

          {/* Sliders Container */}
          <div className="space-y-5">
            {/* Exposure */}
            <div>
              <div className="flex justify-between text-xs font-medium mb-1.5 text-stone-300">
                <span className="flex items-center gap-1.5">
                  <Sun className="w-3.5 h-3.5 text-amber-400" />
                  Exposure
                </span>
                <span className="font-mono text-stone-400">
                  {exposure > 0 ? `+${exposure.toFixed(2)}` : exposure.toFixed(2)}
                </span>
              </div>
              <input
                type="range"
                min="-1"
                max="1"
                step="0.02"
                value={exposure}
                onChange={(e) => {
                  setExposure(parseFloat(e.target.value));
                  setActivePreset(null);
                }}
                className="w-full accent-amber-500 cursor-pointer h-1.5 bg-stone-800 rounded-lg appearance-none"
              />
            </div>

            {/* Warmth (whiteBalanceShiftK) */}
            <div>
              <div className="flex justify-between text-xs font-medium mb-1.5 text-stone-300">
                <span className="flex items-center gap-1.5">
                  <Thermometer className="w-3.5 h-3.5 text-orange-400" />
                  Warmth (WB Tint)
                </span>
                <span className="font-mono text-stone-400">
                  {warmth > 0 ? `+${Math.round(warmth * 2000)}K` : `${Math.round(warmth * 2000)}K`}
                </span>
              </div>
              <input
                type="range"
                min="-1"
                max="1"
                step="0.02"
                value={warmth}
                onChange={(e) => {
                  setWarmth(parseFloat(e.target.value));
                  setActivePreset(null);
                }}
                className="w-full accent-orange-500 cursor-pointer h-1.5 bg-stone-800 rounded-lg appearance-none"
              />
            </div>

            {/* Saturation */}
            <div>
              <div className="flex justify-between text-xs font-medium mb-1.5 text-stone-300">
                <span className="flex items-center gap-1.5">
                  <Palette className="w-3.5 h-3.5 text-rose-400" />
                  Saturation
                </span>
                <span className="font-mono text-stone-400">
                  {saturation > 0 ? `+${saturation.toFixed(2)}` : saturation.toFixed(2)}
                </span>
              </div>
              <input
                type="range"
                min="-1"
                max="1"
                step="0.02"
                value={saturation}
                onChange={(e) => {
                  setSaturation(parseFloat(e.target.value));
                  setActivePreset(null);
                }}
                className="w-full accent-rose-500 cursor-pointer h-1.5 bg-stone-800 rounded-lg appearance-none"
              />
            </div>

            {/* Contrast */}
            <div>
              <div className="flex justify-between text-xs font-medium mb-1.5 text-stone-300">
                <span className="flex items-center gap-1.5">
                  <Contrast className="w-3.5 h-3.5 text-indigo-400" />
                  Contrast
                </span>
                <span className="font-mono text-stone-400">
                  {contrast > 0 ? `+${contrast.toFixed(2)}` : contrast.toFixed(2)}
                </span>
              </div>
              <input
                type="range"
                min="-1"
                max="1"
                step="0.02"
                value={contrast}
                onChange={(e) => {
                  setContrast(parseFloat(e.target.value));
                  setActivePreset(null);
                }}
                className="w-full accent-indigo-500 cursor-pointer h-1.5 bg-stone-800 rounded-lg appearance-none"
              />
            </div>

            {/* Shadows Lift */}
            <div>
              <div className="flex justify-between text-xs font-medium mb-1.5 text-stone-300">
                <span>Shadows Lift (Tone Curve)</span>
                <span className="font-mono text-stone-400">+{shadowsLift.toFixed(2)}</span>
              </div>
              <input
                type="range"
                min="0"
                max="1"
                step="0.02"
                value={shadowsLift}
                onChange={(e) => {
                  setShadowsLift(parseFloat(e.target.value));
                  setActivePreset(null);
                }}
                className="w-full accent-amber-500 cursor-pointer h-1.5 bg-stone-800 rounded-lg appearance-none"
              />
            </div>

            {/* Crop Inset */}
            <div>
              <div className="flex justify-between text-xs font-medium mb-1.5 text-stone-300">
                <span className="flex items-center gap-1.5">
                  <Crop className="w-3.5 h-3.5 text-stone-400" />
                  Crop Inset Tightness
                </span>
                <span className="font-mono text-stone-400">{Math.round(cropInset * 100)}%</span>
              </div>
              <input
                type="range"
                min="0"
                max="0.25"
                step="0.01"
                value={cropInset}
                onChange={(e) => setCropInset(parseFloat(e.target.value))}
                className="w-full accent-stone-400 cursor-pointer h-1.5 bg-stone-800 rounded-lg appearance-none"
              />
            </div>
          </div>

          {/* Confirm Button */}
          <div className="pt-4 border-t border-stone-800">
            <button
              onClick={() => onConfirmLook(currentParams, activePhoto)}
              disabled={isAnalyzing}
              className="w-full py-3.5 px-4 rounded-xl font-bold text-sm bg-gradient-to-r from-amber-500 via-orange-500 to-amber-500 hover:from-amber-400 hover:to-orange-400 text-stone-950 shadow-lg shadow-amber-950/50 transition-all flex items-center justify-center gap-2 disabled:opacity-50"
            >
              {isAnalyzing ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin" />
                  <span>Analyzing Style with VLM...</span>
                </>
              ) : (
                <>
                  <Sparkles className="w-4 h-4" />
                  <span>This is the look — apply across the trip</span>
                </>
              )}
            </button>
            <p className="text-[11px] text-stone-500 text-center mt-2">
              VLM analyzes your before/after intent and plans bespoke params for every other photo in this trip.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
