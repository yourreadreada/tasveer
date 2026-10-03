import React, { useState, useEffect } from 'react';
import {
  ArrowLeft,
  Check,
  Sparkles,
  MessageSquare,
  Download,
  RefreshCw,
  Eye,
  Sliders,
  AlertCircle,
  Database,
  Undo2,
  RotateCcw,
} from 'lucide-react';
import { Photo, PhotoEditParams, StyleDescription, Trip } from '../types';
import { ImageProcessor } from '../utils/imageProcessor';

interface ProcessedItem {
  photo: Photo;
  params: PhotoEditParams;
  initialParams: PhotoEditParams;
  renderedUrl: string;
  isProcessing: boolean;
  wasCorrected: boolean;
  correctionNote?: string;
}

interface BatchReviewScreenProps {
  trip: Trip;
  style: StyleDescription;
  referenceParams: PhotoEditParams;
  referencePhoto: Photo;
  onBackToTrips: () => void;
  onLogEdit: (
    photoId: string | number,
    styleSummary: string,
    initialParams: PhotoEditParams,
    finalParams: PhotoEditParams,
    wasCorrected: boolean,
    correctionNote?: string
  ) => void;
}

export const BatchReviewScreen: React.FC<BatchReviewScreenProps> = ({
  trip,
  style,
  referenceParams,
  referencePhoto,
  onBackToTrips,
  onLogEdit,
}) => {
  const [items, setItems] = useState<ProcessedItem[]>([]);
  const [history, setHistory] = useState<ProcessedItem[][]>([]);
  const [selectedItem, setSelectedItem] = useState<ProcessedItem | null>(null);
  const [correctionInput, setCorrectionInput] = useState<string>('');
  const [isReevaluating, setIsReevaluating] = useState<boolean>(false);
  const [compareOriginal, setCompareOriginal] = useState<boolean>(false);
  const [isBatchRunning, setIsBatchRunning] = useState<boolean>(true);
  const [statusNotification, setStatusNotification] = useState<string | null>(null);

  // Initialize and run the batch orchestration loop across the trip's photos
  useEffect(() => {
    let isCancelled = false;

    async function runBatchPipeline() {
      setIsBatchRunning(true);

      const targets = trip.photos;

      // 1. Create unadjusted baseline snapshot for undo support
      const baselineSnapshot: ProcessedItem[] = targets.map((photo) => ({
        photo,
        params: {
          photoId: photo.id,
          exposureShift: 0,
          whiteBalanceShiftK: 0,
          saturationShift: 0,
          contrastShift: 0,
          shadowsLift: 0,
          cropLeft: 0,
          cropTop: 0,
          cropRight: 1,
          cropBottom: 1,
          blurBackground: false,
          reasoning: 'Original unadjusted photo settings (baseline)',
        },
        initialParams: {
          photoId: photo.id,
          exposureShift: 0,
          whiteBalanceShiftK: 0,
          saturationShift: 0,
          contrastShift: 0,
          shadowsLift: 0,
          cropLeft: 0,
          cropTop: 0,
          cropRight: 1,
          cropBottom: 1,
          blurBackground: false,
          reasoning: 'Original unadjusted photo settings (baseline)',
        },
        renderedUrl: photo.uri,
        isProcessing: false,
        wasCorrected: false,
      }));

      // Set baseline into history so user can revert back to unedited state
      setHistory([baselineSnapshot]);

      const initialItems: ProcessedItem[] = targets.map((photo) => {
        const isRef = photo.id === referencePhoto.id;
        return {
          photo,
          params: isRef ? referenceParams : { ...referenceParams, photoId: photo.id },
          initialParams: isRef ? referenceParams : { ...referenceParams, photoId: photo.id },
          renderedUrl: '',
          isProcessing: true,
          wasCorrected: false,
        };
      });

      setItems(initialItems);

      // Extract conditional rules returned during the single calibration call (Requirement 1b)
      let rules: any[] = [];
      try {
        const parsed = JSON.parse(style.rawModelResponse || '{}');
        if (Array.isArray(parsed.rules)) {
          rules = parsed.rules;
        }
      } catch (e) {
        console.warn('Could not parse rules from style rawModelResponse', e);
      }

      // Process each photo using local offline classification + rule matching (ZERO API calls!)
      for (let i = 0; i < targets.length; i++) {
        if (isCancelled) break;
        const photo = targets[i];
        const isRef = photo.id === referencePhoto.id;

        try {
          let plannedParams = referenceParams;

          if (!isRef) {
            // Requirement 2: Local, offline lighting classification without calling any model
            const lighting = await ImageProcessor.classifyLighting(photo.uri);

            // Requirement 3: Match condition against calibration rules and pull parameters
            plannedParams = ImageProcessor.matchAndApplyRules(
              photo.id,
              lighting,
              rules,
              referenceParams
            );
          }

          // Execute pixels on Canvas via ImageProcessor
          const renderedUrl = await ImageProcessor.renderToDataUrl(photo.uri, plannedParams);

          if (!isCancelled) {
            setItems((prev) =>
              prev.map((item, idx) =>
                idx === i
                  ? {
                      ...item,
                      params: plannedParams,
                      initialParams: plannedParams,
                      renderedUrl,
                      isProcessing: false,
                    }
                  : item
              )
            );

            // Silently log to dataset
            onLogEdit(photo.id, style.summary, plannedParams, plannedParams, false);
          }
        } catch (err) {
          console.error(`Error processing photo ${photo.id}:`, err);
          if (!isCancelled) {
            setItems((prev) =>
              prev.map((item, idx) =>
                idx === i ? { ...item, isProcessing: false, renderedUrl: photo.uri } : item
              )
            );
          }
        }
      }

      if (!isCancelled) setIsBatchRunning(false);
    }

    runBatchPipeline();

    return () => {
      isCancelled = true;
    };
  }, [trip, style, referenceParams, referencePhoto]);

  // Undo function: reverts to the previous snapshot of trip settings
  const handleUndo = () => {
    if (history.length === 0) return;

    // Pop the latest snapshot from history
    const previousState = history[history.length - 1];
    const newHistory = history.slice(0, -1);

    setItems(previousState);
    setHistory(newHistory);

    // If modal is open, sync selectedItem to the reverted item
    if (selectedItem) {
      const synced = previousState.find((it) => it.photo.id === selectedItem.photo.id);
      if (synced) setSelectedItem(synced);
    }

    setStatusNotification('Reverted to previous trip edit settings');
    setTimeout(() => setStatusNotification(null), 3500);
  };

  // Revert all photos back to unadjusted raw settings
  const handleRevertAllToOriginal = () => {
    // Record current state before resetting
    setHistory((prev) => [...prev, items]);

    const originalItems: ProcessedItem[] = items.map((item) => ({
      ...item,
      params: {
        photoId: item.photo.id,
        exposureShift: 0,
        whiteBalanceShiftK: 0,
        saturationShift: 0,
        contrastShift: 0,
        shadowsLift: 0,
        cropLeft: 0,
        cropTop: 0,
        cropRight: 1,
        cropBottom: 1,
        blurBackground: false,
        reasoning: 'Reverted to original unedited state',
      },
      renderedUrl: item.photo.uri,
      wasCorrected: false,
      correctionNote: undefined,
    }));

    setItems(originalItems);
    if (selectedItem) {
      const synced = originalItems.find((it) => it.photo.id === selectedItem.photo.id);
      if (synced) setSelectedItem(synced);
    }

    setStatusNotification('All photos reverted to original unedited baseline');
    setTimeout(() => setStatusNotification(null), 3500);
  };

  // Handle "Tell it what went wrong" correction loop (replanWithCorrection)
  const handleCorrection = async () => {
    if (!selectedItem || !correctionInput.trim()) return;

    setIsReevaluating(true);
    try {
      // Save current items into history before applying correction
      setHistory((prev) => [...prev, items]);

      const res = await fetch('/api/vlm/replan-correction', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          photoId: selectedItem.photo.id,
          style,
          previousParams: selectedItem.params,
          correctionNote: correctionInput.trim(),
          photoMetadata: {
            title: selectedItem.photo.title,
            locationName: selectedItem.photo.locationName,
          },
        }),
      });

      if (res.ok) {
        const correctedParams: PhotoEditParams = await res.json();
        const newRenderedUrl = await ImageProcessor.renderToDataUrl(
          selectedItem.photo.uri,
          correctedParams
        );

        const updatedItem: ProcessedItem = {
          ...selectedItem,
          params: correctedParams,
          renderedUrl: newRenderedUrl,
          wasCorrected: true,
          correctionNote: correctionInput.trim(),
        };

        setSelectedItem(updatedItem);

        // Update in items list
        setItems((prev) =>
          prev.map((item) => (item.photo.id === selectedItem.photo.id ? updatedItem : item))
        );

        // Log correction row in EditLogDatabase (Path A -> Path B bridge)
        onLogEdit(
          selectedItem.photo.id,
          style.summary,
          selectedItem.initialParams,
          correctedParams,
          true,
          correctionInput.trim()
        );

        setCorrectionInput('');
        setStatusNotification(`Applied Gemini correction for "${selectedItem.photo.title || 'photo'}"`);
        setTimeout(() => setStatusNotification(null), 3500);
      }
    } catch (err) {
      console.error('Failed to replan correction:', err);
    } finally {
      setIsReevaluating(false);
    }
  };

  const handleDownloadAll = () => {
    items.forEach((item, index) => {
      if (!item.renderedUrl) return;
      const link = document.createElement('a');
      link.href = item.renderedUrl;
      link.download = `tasveer_${trip.id}_${index + 1}.jpg`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    });
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
      {/* Status Notification Toast */}
      {statusNotification && (
        <div className="mb-4 p-3 rounded-xl bg-amber-500/15 border border-amber-500/30 text-amber-200 text-xs sm:text-sm font-medium flex items-center justify-between shadow-lg backdrop-blur-md animate-fade-in">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-amber-400" />
            <span>{statusNotification}</span>
          </div>
          <button
            onClick={() => setStatusNotification(null)}
            className="text-stone-400 hover:text-stone-200 text-xs"
          >
            ✕
          </button>
        </div>
      )}

      {/* Top Banner & Navigation */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
        <div className="flex items-center gap-3">
          <button
            onClick={onBackToTrips}
            className="p-2 rounded-xl bg-stone-900 hover:bg-stone-800 text-stone-300 border border-stone-800 transition-colors"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl sm:text-2xl font-bold text-stone-100">
                Batch AI Review — {trip.title}
              </h1>
              {isBatchRunning ? (
                <span className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-amber-500/10 text-amber-400 border border-amber-500/20 text-xs animate-pulse">
                  <RefreshCw className="w-3 h-3 animate-spin" />
                  Gemini Reasoning
                </span>
              ) : (
                <span className="flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-xs">
                  <Check className="w-3 h-3" />
                  Planned by Gemini
                </span>
              )}
            </div>
            <p className="text-xs text-stone-400">
              Each photo evaluated individually against your calibrated aesthetic intent via Google Gemini.
            </p>
          </div>
        </div>

        {/* Toolbar with Undo and Actions */}
        <div className="flex flex-wrap items-center gap-2 sm:gap-3">
          {/* UNDO FUNCTIONALITY BUTTON */}
          <button
            onClick={handleUndo}
            disabled={history.length === 0 || isBatchRunning}
            className="px-3.5 py-2 rounded-xl text-xs sm:text-sm font-medium bg-stone-900 hover:bg-stone-800 text-stone-200 border border-stone-700/80 transition-all flex items-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed shadow-sm"
            title={
              history.length > 0
                ? `Revert to previous settings (${history.length} snapshot${history.length > 1 ? 's' : ''} available)`
                : 'No previous edits to revert'
            }
          >
            <Undo2 className="w-4 h-4 text-amber-400" />
            <span>Undo Edits</span>
            {history.length > 0 && (
              <span className="ml-0.5 px-1.5 py-0.2 rounded-full text-[10px] bg-amber-500/20 text-amber-300 font-mono">
                {history.length}
              </span>
            )}
          </button>

          <button
            onClick={handleRevertAllToOriginal}
            disabled={isBatchRunning}
            className="px-3 py-2 rounded-xl text-xs sm:text-sm font-medium bg-stone-900 hover:bg-stone-800 text-stone-300 border border-stone-800 transition-all flex items-center gap-1.5 disabled:opacity-40"
            title="Revert all photos back to initial unadjusted state"
          >
            <RotateCcw className="w-3.5 h-3.5 text-stone-400" />
            <span className="hidden sm:inline">Reset to Original</span>
          </button>

          <button
            onClick={handleDownloadAll}
            disabled={isBatchRunning}
            className="px-3.5 py-2 rounded-xl text-xs sm:text-sm font-medium bg-stone-800 hover:bg-stone-700 text-stone-200 border border-stone-700 transition-all flex items-center gap-1.5 disabled:opacity-50"
          >
            <Download className="w-4 h-4 text-amber-400" />
            <span>Download All ({items.length})</span>
          </button>

          <button
            onClick={onBackToTrips}
            className="px-4 py-2 rounded-xl text-xs sm:text-sm font-bold bg-amber-500 hover:bg-amber-400 text-stone-950 transition-all flex items-center gap-1.5 shadow-lg shadow-amber-950/30"
          >
            <Check className="w-4 h-4" />
            <span>Accept Trip Edits</span>
          </button>
        </div>
      </div>

      {/* Semantic Style Card (Extracted by Gemini analyzeStyle) */}
      <div className="p-4 sm:p-5 rounded-2xl bg-stone-900/80 border border-stone-800 shadow-md mb-8">
        <div className="flex items-center gap-2 mb-2 text-amber-400 font-semibold text-xs tracking-wide uppercase">
          <Sparkles className="w-4 h-4" />
          Extracted Style Intent (Gemini 3.8 Flash)
        </div>
        <p className="text-sm font-medium text-stone-200 mb-2 leading-relaxed">
          "{style.summary}"
        </p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs text-stone-400 pt-3 border-t border-stone-800">
          <div>
            <span className="font-semibold text-stone-300">Tone Guidance: </span>
            {style.toneNotes}
          </div>
          <div>
            <span className="font-semibold text-stone-300">Crop Philosophy: </span>
            {style.cropNotes}
          </div>
        </div>
      </div>

      {/* Review Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        {items.map((item) => {
          const isRef = item.photo.id === referencePhoto.id;

          return (
            <div
              key={item.photo.id}
              onClick={() => setSelectedItem(item)}
              className="group bg-stone-900 rounded-2xl overflow-hidden border border-stone-800 hover:border-amber-500/50 hover:shadow-xl transition-all cursor-pointer flex flex-col"
            >
              {/* Image Preview Container */}
              <div className="relative aspect-[4/3] bg-stone-950 overflow-hidden">
                {item.isProcessing ? (
                  <div className="w-full h-full flex flex-col items-center justify-center gap-2 text-stone-500">
                    <RefreshCw className="w-6 h-6 animate-spin text-amber-400" />
                    <span className="text-xs">Reasoning with Gemini...</span>
                  </div>
                ) : (
                  <img
                    src={item.renderedUrl || item.photo.uri}
                    alt={item.photo.title || 'Processed photo'}
                    className="w-full h-full object-cover group-hover:scale-102 transition-transform duration-300"
                  />
                )}

                {/* Badges */}
                <div className="absolute top-3 left-3 flex items-center gap-1.5">
                  {isRef && (
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-500 text-stone-950 shadow">
                      Reference
                    </span>
                  )}
                  {item.wasCorrected && (
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">
                      Corrected
                    </span>
                  )}
                </div>

                <div className="absolute top-3 right-3 px-2 py-1 rounded-md bg-stone-950/70 backdrop-blur-md text-[10px] font-mono text-stone-300 border border-white/10 opacity-0 group-hover:opacity-100 transition-opacity">
                  Click to inspect
                </div>
              </div>

              {/* Card Meta & Reasoning */}
              <div className="p-4 flex flex-col justify-between flex-1 gap-3">
                <div>
                  <h3 className="font-semibold text-sm text-stone-200 truncate">
                    {item.photo.title || 'Trip Photo'}
                  </h3>
                  <p className="text-xs text-stone-400 mt-1 line-clamp-2 italic">
                    "{item.params.reasoning || 'Calibrated to target style'}"
                  </p>
                </div>

                {/* Micro parameter pills */}
                <div className="pt-2 border-t border-stone-800 flex items-center justify-between text-[11px] text-stone-400 font-mono">
                  <span>
                    Exp: {item.params.exposureShift >= 0 ? `+${item.params.exposureShift}` : item.params.exposureShift}
                  </span>
                  <span>
                    WB: {item.params.whiteBalanceShiftK >= 0 ? `+${item.params.whiteBalanceShiftK}` : item.params.whiteBalanceShiftK}K
                  </span>
                  <span className="text-amber-400 text-xs font-sans flex items-center gap-1 font-medium">
                    <MessageSquare className="w-3 h-3" />
                    Correct
                  </span>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Single Photo Inspector & "Tell it what went wrong" Correction Loop Modal */}
      {selectedItem && (
        <div className="fixed inset-0 z-50 bg-stone-950/80 backdrop-blur-md flex items-center justify-center p-4">
          <div className="bg-stone-900 border border-stone-800 rounded-3xl max-w-4xl w-full max-h-[92vh] flex flex-col overflow-hidden shadow-2xl">
            {/* Modal Header */}
            <div className="px-6 py-4 border-b border-stone-800 flex items-center justify-between">
              <div>
                <h3 className="text-lg font-bold text-stone-100">
                  {selectedItem.photo.title || 'Photo Inspection'}
                </h3>
                <p className="text-xs text-stone-400">
                  Per-photo adjustments and interactive feedback loop via Gemini API
                </p>
              </div>
              <button
                onClick={() => setSelectedItem(null)}
                className="w-8 h-8 rounded-full bg-stone-800 hover:bg-stone-700 text-stone-300 flex items-center justify-center transition-colors"
              >
                ✕
              </button>
            </div>

            {/* Modal Body */}
            <div className="flex-1 overflow-y-auto p-6 grid grid-cols-1 md:grid-cols-2 gap-6 items-start">
              {/* Photo Preview with Compare */}
              <div className="flex flex-col gap-3">
                <div className="relative aspect-[4/3] rounded-2xl overflow-hidden bg-stone-950 border border-stone-800 flex items-center justify-center">
                  <img
                    src={compareOriginal ? selectedItem.photo.uri : (selectedItem.renderedUrl || selectedItem.photo.uri)}
                    alt="Comparison"
                    className="max-h-full max-w-full object-contain"
                  />
                  <div className="absolute top-3 left-3 px-2 py-1 rounded bg-stone-950/80 backdrop-blur text-[10px] font-mono text-stone-300 border border-white/10">
                    {compareOriginal ? 'BEFORE (Raw Original)' : 'AFTER (Gemini Planned)'}
                  </div>
                </div>

                <div className="flex justify-between items-center text-xs">
                  <button
                    onMouseDown={() => setCompareOriginal(true)}
                    onMouseUp={() => setCompareOriginal(false)}
                    onTouchStart={() => setCompareOriginal(true)}
                    onTouchEnd={() => setCompareOriginal(false)}
                    className="px-3 py-1.5 rounded-lg bg-stone-800 hover:bg-stone-700 text-stone-200 border border-stone-700 font-medium flex items-center gap-1.5 transition-colors"
                  >
                    <Eye className="w-3.5 h-3.5" />
                    <span>Hold to View Original</span>
                  </button>
                  <span className="text-stone-500 font-mono text-[11px]">
                    ID: {selectedItem.photo.id}
                  </span>
                </div>
              </div>

              {/* Parameter Reasoning & Feedback Form */}
              <div className="flex flex-col gap-5">
                {/* AI Reasoning Box */}
                <div className="p-4 rounded-xl bg-stone-950 border border-stone-800">
                  <div className="text-xs font-semibold text-amber-400 mb-1 flex items-center gap-1.5">
                    <Sparkles className="w-3.5 h-3.5" />
                    Gemini Intent for this photo
                  </div>
                  <p className="text-xs text-stone-300 italic leading-relaxed">
                    "{selectedItem.params.reasoning || 'Harmonized with reference style.'}"
                  </p>
                </div>

                {/* Calculated Parameters Table */}
                <div className="grid grid-cols-2 gap-2 text-xs font-mono bg-stone-950/50 p-3 rounded-xl border border-stone-800/60">
                  <div className="text-stone-400">Exposure Shift:</div>
                  <div className="text-stone-200 font-semibold">{selectedItem.params.exposureShift}</div>

                  <div className="text-stone-400">WB Tint (K):</div>
                  <div className="text-stone-200 font-semibold">{selectedItem.params.whiteBalanceShiftK}K</div>

                  <div className="text-stone-400">Saturation:</div>
                  <div className="text-stone-200 font-semibold">{selectedItem.params.saturationShift}</div>

                  <div className="text-stone-400">Contrast:</div>
                  <div className="text-stone-200 font-semibold">{selectedItem.params.contrastShift}</div>

                  <div className="text-stone-400">Shadows Lift:</div>
                  <div className="text-stone-200 font-semibold">{selectedItem.params.shadowsLift}</div>
                </div>

                {/* "Tell it what went wrong" Correction Loop (VLMClient.replanWithCorrection) */}
                <div className="pt-2 border-t border-stone-800 flex flex-col gap-2">
                  <label className="text-xs font-bold text-stone-200 flex items-center justify-between">
                    <span>Tell Gemini what went wrong:</span>
                    <span className="text-[10px] text-amber-400 font-normal">Correction Loop</span>
                  </label>
                  <p className="text-[11px] text-stone-400">
                    If something looks off (e.g. "Too warm for an overcast sky", "Shadows need more lift", "Too saturated"), tell the model:
                  </p>
                  <textarea
                    rows={2}
                    value={correctionInput}
                    onChange={(e) => setCorrectionInput(e.target.value)}
                    placeholder="e.g., A bit too warm; make it cooler and lift the deep shadows"
                    className="w-full text-xs p-3 rounded-xl bg-stone-950 border border-stone-700 text-stone-100 placeholder-stone-600 focus:outline-none focus:border-amber-400 focus:ring-1 focus:ring-amber-400"
                  />
                  <div className="flex gap-2">
                    <button
                      onClick={handleCorrection}
                      disabled={isReevaluating || !correctionInput.trim()}
                      className="flex-1 py-2.5 px-4 rounded-xl text-xs font-bold bg-amber-500 hover:bg-amber-400 text-stone-950 transition-all flex items-center justify-center gap-1.5 disabled:opacity-50"
                    >
                      {isReevaluating ? (
                        <>
                          <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                          <span>Re-reasoning with Gemini...</span>
                        </>
                      ) : (
                        <>
                          <Sparkles className="w-3.5 h-3.5" />
                          <span>Re-reason with correction</span>
                        </>
                      )}
                    </button>
                  </div>
                  {selectedItem.wasCorrected && (
                    <div className="p-2.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-[11px] text-emerald-300">
                      Previous feedback: "{selectedItem.correctionNote}" — Logged to training dataset row.
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="px-6 py-4 bg-stone-950 border-t border-stone-800 flex justify-end">
              <button
                onClick={() => setSelectedItem(null)}
                className="px-5 py-2 rounded-xl text-xs font-semibold bg-stone-800 hover:bg-stone-700 text-stone-200 transition-colors"
              >
                Done Inspecting
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
