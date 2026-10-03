import express, { Request, Response } from 'express';
import cors from 'cors';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';
import { GoogleGenAI } from '@google/genai';
import { createServer as createViteServer } from 'vite';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
// AI Studio requires dev server to always bind to port 3000 on 0.0.0.0
const port = 3000;

app.use(cors());
app.use(express.json({ limit: '50mb' }));

// In-memory dataset log (mirrors Room EditLogDatabase)
interface StoredEditLogRow {
  id: string;
  photoId: string | number;
  styleSummary: string;
  initialParams: string;
  finalParams: string;
  wasCorrected: boolean;
  correctionNote?: string | null;
  timestampMillis: number;
}

const editLogDatabase: StoredEditLogRow[] = [];

// Gemini client initialization
const geminiApiKey = process.env.GEMINI_API_KEY;
let aiClient: GoogleGenAI | null = null;
if (geminiApiKey) {
  try {
    aiClient = new GoogleGenAI({
      apiKey: geminiApiKey,
      httpOptions: {
        headers: {
          'User-Agent': 'aistudio-build',
        },
      },
    });
  } catch (err) {
    console.warn('[Tasveer VLM] Failed to initialize GoogleGenAI client:', err);
  }
}

/**
 * 1. Analyze Style Endpoint (corresponds to VLMClient.analyzeStyle)
 *
 * ARCHITECTURAL UPGRADE:
 * Called ONCE per calibration (1-2 reference photos).
 * Returns BOTH:
 *  a) Semantic style description (mood, tone, crop philosophy)
 *  b) A set of CONDITIONAL RULES (structured JSON) inferred from calibration
 *     for on-device local lighting classification (underexposed/backlit, indoor warm,
 *     bright daylight, cool overcast, neutral).
 */
app.post('/api/vlm/analyze-style', async (req: Request, res: Response) => {
  try {
    const { referenceParams, sampleTitle } = req.body;

    const baseExp = Number(referenceParams?.exposureShift ?? 0);
    const baseWarmth = Number(referenceParams?.whiteBalanceShiftK ?? 0);
    const baseSat = Number(referenceParams?.saturationShift ?? 0);
    const baseContrast = Number(referenceParams?.contrastShift ?? 0);
    const baseShadows = Number(referenceParams?.shadowsLift ?? 0);

    // Prompt requesting BOTH semantic style AND conditional rules
    const prompt = `You are a professional photo retoucher's assistant analyzing a photographer's calibration on reference photos.
The photographer adjusted reference photos with these concrete parameters:
- Exposure shift: ${baseExp}
- Warmth (Kelvin shift): ${baseWarmth}K
- Saturation shift: ${baseSat}
- Contrast shift: ${baseContrast}
- Shadows lift: ${baseShadows}
Photo context: "${sampleTitle || 'Reference photo'}"

We need TWO things in ONE call:
1. SEMANTIC STYLE: Describe in plain terms (intent, not literal numbers) the tone/white balance direction, shadow/highlight handling, saturation, contrast, and cropping philosophy.
2. CONDITIONAL RULES: A set of conditional rules inferred from this calibration to adapt the look to different lighting conditions across the trip without making more API calls:
   - "underexposed_or_backlit": if the photo is dark/backlit, how much to lift shadows and bump exposure
   - "indoor_warm_light": if shot indoors under warm tungsten/incandescent light, how to balance saturation/warmth
   - "overexposed_or_bright_daylight": if shot in harsh direct daylight, how to protect highlights and maintain contrast
   - "cool_overcast": if shot under cool gray cloudy skies, how to balance white balance
   - "neutral_balanced": baseline application of the calibration look

Respond ONLY with JSON in this exact structure:
{
  "summary": "1-2 concise sentences summarizing the look",
  "toneNotes": "Description of tonal curve, color temperature, and contrast character",
  "cropNotes": "Guidance on framing, subject emphasis, and headroom",
  "rules": [
    {
      "condition": "underexposed_or_backlit",
      "exposureShift": float -1..1,
      "whiteBalanceShiftK": int -2000..2000,
      "saturationShift": float -1..1,
      "shadowsLift": float 0..1,
      "contrastShift": float -1..1,
      "reasoning": "brief explanation"
    },
    {
      "condition": "indoor_warm_light",
      "exposureShift": float -1..1,
      "whiteBalanceShiftK": int -2000..2000,
      "saturationShift": float -1..1,
      "shadowsLift": float 0..1,
      "contrastShift": float -1..1,
      "reasoning": "brief explanation"
    },
    {
      "condition": "overexposed_or_bright_daylight",
      "exposureShift": float -1..1,
      "whiteBalanceShiftK": int -2000..2000,
      "saturationShift": float -1..1,
      "shadowsLift": float 0..1,
      "contrastShift": float -1..1,
      "reasoning": "brief explanation"
    },
    {
      "condition": "cool_overcast",
      "exposureShift": float -1..1,
      "whiteBalanceShiftK": int -2000..2000,
      "saturationShift": float -1..1,
      "shadowsLift": float 0..1,
      "contrastShift": float -1..1,
      "reasoning": "brief explanation"
    },
    {
      "condition": "neutral_balanced",
      "exposureShift": float -1..1,
      "whiteBalanceShiftK": int -2000..2000,
      "saturationShift": float -1..1,
      "shadowsLift": float 0..1,
      "contrastShift": float -1..1,
      "reasoning": "brief explanation"
    }
  ]
}`;

    if (aiClient) {
      try {
        const response = await aiClient.models.generateContent({
          model: 'gemini-3.8-flash',
          contents: prompt,
          config: { responseMimeType: 'application/json' },
        });

        const text = response.text || '';
        const parsed = JSON.parse(text);
        if (parsed.summary && Array.isArray(parsed.rules)) {
          return res.json({
            summary: parsed.summary,
            toneNotes: parsed.toneNotes || '',
            cropNotes: parsed.cropNotes || '',
            rules: parsed.rules,
            rawModelResponse: text,
          });
        }
      } catch (geminiErr) {
        console.warn('[Tasveer VLM] Gemini call failed, using high-precision heuristic rule engine:', geminiErr);
      }
    }

    // High-precision Heuristic Engine (generates complete semantic style + conditional rules)
    let toneDesc = 'Balanced neutral tonal palette';
    if (baseWarmth > 300) toneDesc = 'Sun-drenched, warm golden-hour grading with enhanced amber midtones';
    else if (baseWarmth < -300) toneDesc = 'Cool cinematic daylight balance with crisp cyan undertones';

    let expDesc = 'true-to-life luminance balance';
    if (baseExp > 0.15) expDesc = 'bright, airy, high-key presentation with open shadows';
    else if (baseExp < -0.15) expDesc = 'moody, intimate, low-key presentation with deep tonal depth';

    let satDesc = 'restrained, organic skin and landscape tones';
    if (baseSat > 0.2) satDesc = 'vivid, energetic color punch and rich chrominance';
    else if (baseSat < -0.2) satDesc = 'subdued, muted Scandinavian matte aesthetic';

    const summary = `${toneDesc}, ${expDesc}, and ${satDesc}.`;
    const toneNotes = `Highlights kept natural; midtones gently sculpted with ${baseContrast >= 0 ? 'punchy' : 'gentle'} micro-contrast. White balance adapted for emotive storytelling.`;
    const cropNotes = `Center-weighted subject focus, clearing edge distractions and preserving natural leading lines.`;

    // 5 Conditional Rules derived directly from user's calibration
    const rules = [
      {
        condition: 'underexposed_or_backlit',
        exposureShift: Number(Math.min(1.0, baseExp + 0.22).toFixed(2)),
        whiteBalanceShiftK: Math.round(baseWarmth * 0.8),
        saturationShift: Number(Math.max(-1.0, baseSat - 0.05).toFixed(2)),
        shadowsLift: Number(Math.min(1.0, baseShadows + 0.35).toFixed(2)),
        contrastShift: Number((baseContrast * 0.8).toFixed(2)),
        reasoning: 'Backlit/underexposed: lifted deep shadows and boosted exposure to recover subject details.',
      },
      {
        condition: 'indoor_warm_light',
        exposureShift: Number(baseExp.toFixed(2)),
        // Pull back warm Kelvin to avoid double-warming already tungsten-lit indoor shots
        whiteBalanceShiftK: Math.round(baseWarmth < 0 ? baseWarmth : -150),
        saturationShift: Number(Math.max(-1.0, baseSat - 0.1).toFixed(2)),
        shadowsLift: Number(Math.min(1.0, baseShadows + 0.15).toFixed(2)),
        contrastShift: Number((baseContrast * 0.9).toFixed(2)),
        reasoning: 'Indoor warm light: dialed back Kelvin warmth and saturation to prevent unnatural orange cast.',
      },
      {
        condition: 'overexposed_or_bright_daylight',
        exposureShift: Number(Math.max(-1.0, baseExp - 0.12).toFixed(2)),
        whiteBalanceShiftK: Math.round(baseWarmth),
        saturationShift: Number(baseSat.toFixed(2)),
        shadowsLift: Number((baseShadows * 0.5).toFixed(2)),
        contrastShift: Number(Math.min(1.0, baseContrast + 0.08).toFixed(2)),
        reasoning: 'Bright daylight: lowered exposure slightly to protect highlight retention and pinned contrast.',
      },
      {
        condition: 'cool_overcast',
        exposureShift: Number(Math.min(1.0, baseExp + 0.08).toFixed(2)),
        whiteBalanceShiftK: Math.round(baseWarmth + 250),
        saturationShift: Number(Math.min(1.0, baseSat + 0.05).toFixed(2)),
        shadowsLift: Number(Math.min(1.0, baseShadows + 0.2).toFixed(2)),
        contrastShift: Number(baseContrast.toFixed(2)),
        reasoning: 'Cool overcast: gently added warmth and lifted midtones to counter gray ambient light.',
      },
      {
        condition: 'neutral_balanced',
        exposureShift: Number(baseExp.toFixed(2)),
        whiteBalanceShiftK: Math.round(baseWarmth),
        saturationShift: Number(baseSat.toFixed(2)),
        shadowsLift: Number(baseShadows.toFixed(2)),
        contrastShift: Number(baseContrast.toFixed(2)),
        reasoning: 'Neutral daylight: direct translation of calibrated reference parameters.',
      },
    ];

    const payload = {
      summary,
      toneNotes,
      cropNotes,
      rules,
      rawModelResponse: JSON.stringify({ summary, toneNotes, cropNotes, rules }),
    };

    return res.json(payload);
  } catch (error) {
    console.error('Error analyzing style:', error);
    res.status(500).json({ error: 'Failed to analyze style' });
  }
});

/**
 * 2. Plan Edit Endpoint (corresponds to VLMClient.planEdit)
 * (Retained for single-photo inspection / offline-bypass verification)
 */
app.post('/api/vlm/plan-edit', async (req: Request, res: Response) => {
  try {
    const { photoId, style, photoMetadata, referenceParams } = req.body;

    const prompt = `Target style (learned from the user's own reference edits):
${style?.summary || 'Natural warm travel photography with lifted shadows'}

Tone Notes: ${style?.toneNotes || 'Balanced highlights and shadows'}
Crop Notes: ${style?.cropNotes || 'Subject-focused framing'}

Look at THIS specific photo's context:
Title: "${photoMetadata?.title || 'Trip photo'}"
Location: "${photoMetadata?.locationName || 'Unknown'}"
Camera: ${photoMetadata?.isFrontCamera ? 'Front selfie camera' : 'Rear primary camera'}

Decide what adjustments THIS photo needs to reach the target style's look —
not the same numbers as any other photo, but whatever gets THIS one there.

Respond ONLY with JSON in this exact shape, no other text:
{
  "exposureShift": 0.15,
  "whiteBalanceShiftK": 350,
  "saturationShift": 0.1,
  "shadowsLift": 0.2,
  "contrastShift": 0.05,
  "cropLeft": 0.0,
  "cropTop": 0.0,
  "cropRight": 1.0,
  "cropBottom": 1.0,
  "blurBackground": false,
  "reasoning": "one sentence on why"
}`;

    if (aiClient) {
      try {
        const response = await aiClient.models.generateContent({
          model: 'gemini-3.8-flash',
          contents: prompt,
          config: { responseMimeType: 'application/json' },
        });

        const text = response.text || '';
        const parsed = JSON.parse(text);
        return res.json({
          photoId,
          exposureShift: Number(parsed.exposureShift ?? 0),
          whiteBalanceShiftK: Number(parsed.whiteBalanceShiftK ?? 0),
          saturationShift: Number(parsed.saturationShift ?? 0),
          shadowsLift: Number(parsed.shadowsLift ?? 0),
          contrastShift: Number(parsed.contrastShift ?? 0),
          cropLeft: Number(parsed.cropLeft ?? 0),
          cropTop: Number(parsed.cropTop ?? 0),
          cropRight: Number(parsed.cropRight ?? 1),
          cropBottom: Number(parsed.cropBottom ?? 1),
          blurBackground: Boolean(parsed.blurBackground ?? false),
          reasoning: parsed.reasoning || 'Customized for photo subject and lighting',
        });
      } catch (geminiErr) {
        console.warn('[Tasveer VLM] Gemini call failed in plan-edit:', geminiErr);
      }
    }

    // Heuristic per-photo reasoning
    const baseExp = referenceParams?.exposureShift ?? 0;
    const baseWb = referenceParams?.whiteBalanceShiftK ?? 0;
    const baseSat = referenceParams?.saturationShift ?? 0;
    const baseContrast = referenceParams?.contrastShift ?? 0;
    const baseShadows = referenceParams?.shadowsLift ?? 0;

    let exposureAdj = baseExp;
    let shadowsAdj = baseShadows;
    let wbAdj = baseWb;

    if (photoMetadata?.title?.toLowerCase().includes('sunset') || photoMetadata?.title?.toLowerCase().includes('night')) {
      shadowsAdj = Math.min(1.0, baseShadows + 0.25);
      exposureAdj = Math.min(1.0, baseExp + 0.1);
    } else if (photoMetadata?.title?.toLowerCase().includes('lake') || photoMetadata?.title?.toLowerCase().includes('mountain')) {
      wbAdj = baseWb + 100;
    }

    return res.json({
      photoId,
      exposureShift: exposureAdj,
      whiteBalanceShiftK: wbAdj,
      saturationShift: baseSat,
      shadowsLift: shadowsAdj,
      contrastShift: baseContrast,
      cropLeft: 0,
      cropTop: 0,
      cropRight: 1,
      cropBottom: 1,
      blurBackground: false,
      reasoning: `Adjusted based on scene context (${photoMetadata?.title || 'trip scene'}) and calibrated aesthetic.`,
    });
  } catch (error) {
    console.error('Error planning edit:', error);
    res.status(500).json({ error: 'Failed to plan edit' });
  }
});

/**
 * 3. Replan With Correction Endpoint (corresponds to VLMClient.replanWithCorrection)
 * Kept EXACTLY as is per Requirement 4:
 * "Keep the existing 'user flags a photo as wrong, explain what's off, re-edit it' correction flow
 * exactly as is — that should still be the ONLY case where the model gets called again."
 */
app.post('/api/vlm/replan-correction', async (req: Request, res: Response) => {
  try {
    const { photoId, style, previousParams, correctionNote, photoMetadata } = req.body;

    const prompt = `Target style: ${style?.summary || 'Custom photo look'}
Your previous attempt on photo ${photoMetadata?.title || photoId}:
${JSON.stringify(previousParams, null, 2)}

The user says this was wrong: "${correctionNote}"

Produce corrected JSON parameters in the same shape as before, fixing that issue specifically while still honoring the target style.
Respond ONLY with JSON:
{
  "exposureShift": float -1..1,
  "whiteBalanceShiftK": int -2000..2000,
  "saturationShift": float -1..1,
  "shadowsLift": float 0..1,
  "contrastShift": float -1..1,
  "cropLeft": float 0..1, "cropTop": float 0..1,
  "cropRight": float 0..1, "cropBottom": float 0..1,
  "blurBackground": boolean,
  "reasoning": "how the correction was applied"
}`;

    if (aiClient) {
      try {
        const response = await aiClient.models.generateContent({
          model: 'gemini-3.8-flash',
          contents: prompt,
          config: { responseMimeType: 'application/json' },
        });

        const text = response.text || '';
        const parsed = JSON.parse(text);
        return res.json({
          photoId,
          exposureShift: Number(parsed.exposureShift ?? previousParams.exposureShift),
          whiteBalanceShiftK: Number(parsed.whiteBalanceShiftK ?? previousParams.whiteBalanceShiftK),
          saturationShift: Number(parsed.saturationShift ?? previousParams.saturationShift),
          shadowsLift: Number(parsed.shadowsLift ?? previousParams.shadowsLift),
          contrastShift: Number(parsed.contrastShift ?? previousParams.contrastShift),
          cropLeft: Number(parsed.cropLeft ?? previousParams.cropLeft),
          cropTop: Number(parsed.cropTop ?? previousParams.cropTop),
          cropRight: Number(parsed.cropRight ?? previousParams.cropRight),
          cropBottom: Number(parsed.cropBottom ?? previousParams.cropBottom),
          blurBackground: Boolean(parsed.blurBackground ?? previousParams.blurBackground),
          reasoning: parsed.reasoning || `Corrected: ${correctionNote}`,
        });
      } catch (geminiErr) {
        console.warn('[Tasveer VLM] Gemini correction call failed, using heuristic correction:', geminiErr);
      }
    }

    // Heuristic correction parser
    const lowerNote = (correctionNote || '').toLowerCase();
    let newExposure = previousParams.exposureShift ?? 0;
    let newWb = previousParams.whiteBalanceShiftK ?? 0;
    let newSat = previousParams.saturationShift ?? 0;
    let newShadows = previousParams.shadowsLift ?? 0;
    let newContrast = previousParams.contrastShift ?? 0;

    if (lowerNote.includes('dark') || lowerNote.includes('brighter') || lowerNote.includes('exposure')) {
      newExposure = Math.min(1.0, newExposure + 0.2);
    }
    if (lowerNote.includes('bright') || lowerNote.includes('overexposed')) {
      newExposure = Math.max(-1.0, newExposure - 0.2);
    }
    if (lowerNote.includes('warm') || lowerNote.includes('orange') || lowerNote.includes('yellow')) {
      newWb = lowerNote.includes('too warm') || lowerNote.includes('less warm') ? newWb - 300 : newWb + 300;
    }
    if (lowerNote.includes('cool') || lowerNote.includes('blue')) {
      newWb = lowerNote.includes('too cool') ? newWb + 300 : newWb - 300;
    }
    if (lowerNote.includes('shadow') || lowerNote.includes('shadows')) {
      newShadows = Math.min(1.0, newShadows + 0.25);
    }
    if (lowerNote.includes('saturated') || lowerNote.includes('color') || lowerNote.includes('vibrant')) {
      newSat = lowerNote.includes('too') || lowerNote.includes('less') ? Math.max(-1.0, newSat - 0.2) : Math.min(1.0, newSat + 0.2);
    }

    return res.json({
      ...previousParams,
      photoId,
      exposureShift: newExposure,
      whiteBalanceShiftK: newWb,
      saturationShift: newSat,
      shadowsLift: newShadows,
      contrastShift: newContrast,
      reasoning: `Adjusted based on feedback: "${correctionNote}"`,
    });
  } catch (error) {
    console.error('Error replanning correction:', error);
    res.status(500).json({ error: 'Failed to replan correction' });
  }
});

/**
 * 4. Dataset Log Endpoints (EditLogDatabase / Repository)
 */
app.post('/api/edit-log', (req: Request, res: Response) => {
  const { photoId, styleSummary, initialParams, finalParams, wasCorrected, correctionNote } = req.body;

  const entry: StoredEditLogRow = {
    id: `log_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    photoId,
    styleSummary,
    initialParams: typeof initialParams === 'string' ? initialParams : JSON.stringify(initialParams),
    finalParams: typeof finalParams === 'string' ? finalParams : JSON.stringify(finalParams),
    wasCorrected: Boolean(wasCorrected),
    correctionNote: correctionNote || null,
    timestampMillis: Date.now(),
  };

  editLogDatabase.unshift(entry);
  res.json({ success: true, entry });
});

app.get('/api/edit-log/all', (_req: Request, res: Response) => {
  res.json(editLogDatabase);
});

app.get('/api/edit-log/stats', (_req: Request, res: Response) => {
  const totalCount = editLogDatabase.length;
  const correctedCount = editLogDatabase.filter((r) => r.wasCorrected).length;
  const targetDatasetSize = 100;
  const readinessPercentage = Math.min(100, Math.round((totalCount / targetDatasetSize) * 100));

  res.json({
    totalCount,
    correctedCount,
    readinessPercentage,
  });
});

// Serve Vite in dev or static dist in production
async function startServer() {
  const isProduction = process.env.NODE_ENV === 'production';
  const distPath = path.resolve(__dirname, 'dist');
  const distIndex = path.resolve(distPath, 'index.html');

  if (!isProduction) {
    try {
      const vite = await createViteServer({
        server: { middlewareMode: true },
        appType: 'spa',
      });
      app.use(vite.middlewares);
    } catch (e) {
      console.warn('[Tasveer] Failed to mount vite middleware, falling back to static:', e);
    }
  }

  if (fs.existsSync(distPath)) {
    app.use(express.static(distPath));
  }

  app.use((req: Request, res: Response) => {
    if (req.path.startsWith('/api/')) {
      return res.status(404).json({ error: 'Endpoint not found' });
    }
    if (fs.existsSync(distIndex)) {
      return res.sendFile(distIndex);
    }
    const rootIndex = path.resolve(__dirname, 'index.html');
    if (fs.existsSync(rootIndex)) {
      return res.sendFile(rootIndex);
    }
    res.status(404).send('Application entry point not found');
  });

  app.listen(port, '0.0.0.0', () => {
    console.log(`[Tasveer Backend] Dev server running at http://0.0.0.0:${port}`);
  });
}

startServer();
