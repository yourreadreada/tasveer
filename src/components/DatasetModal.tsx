import React from 'react';
import { Database, Download, CheckCircle, RefreshCw, X, FileJson } from 'lucide-react';
import { EditLogEntry } from '../types';

interface DatasetModalProps {
  isOpen: boolean;
  onClose: () => void;
  entries: EditLogEntry[];
}

export const DatasetModal: React.FC<DatasetModalProps> = ({ isOpen, onClose, entries }) => {
  if (!isOpen) return null;

  const correctedCount = entries.filter((e) => e.wasCorrected).length;

  const handleExportJSON = () => {
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(entries, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute('href', dataStr);
    downloadAnchor.setAttribute('download', `tasveer_training_dataset_${Date.now()}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  return (
    <div className="fixed inset-0 z-50 bg-stone-950/80 backdrop-blur-md flex items-center justify-center p-4">
      <div className="bg-stone-900 border border-stone-800 rounded-3xl max-w-3xl w-full max-h-[85vh] flex flex-col overflow-hidden shadow-2xl">
        {/* Header */}
        <div className="px-6 py-5 border-b border-stone-800 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
              <Database className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-stone-100">Dataset Logging (Path A → Path B)</h2>
              <p className="text-xs text-stone-400">
                Silently logging every AI attempt and human correction to train future on-device models.
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-full bg-stone-800 hover:bg-stone-700 text-stone-300 flex items-center justify-center transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Stats Row */}
        <div className="px-6 py-4 bg-stone-950/60 border-b border-stone-800/80 grid grid-cols-3 gap-4">
          <div className="p-3 rounded-xl bg-stone-900 border border-stone-800">
            <div className="text-[11px] text-stone-400 font-medium">Logged Edits</div>
            <div className="text-xl font-bold text-stone-100 font-mono mt-0.5">{entries.length}</div>
          </div>
          <div className="p-3 rounded-xl bg-stone-900 border border-stone-800">
            <div className="text-[11px] text-stone-400 font-medium">Human Corrections</div>
            <div className="text-xl font-bold text-amber-400 font-mono mt-0.5">{correctedCount}</div>
          </div>
          <div className="p-3 rounded-xl bg-stone-900 border border-stone-800 flex items-center justify-between">
            <div>
              <div className="text-[11px] text-stone-400 font-medium">Path B Export</div>
              <div className="text-xs font-semibold text-emerald-400 mt-0.5">Ready for Fine-tuning</div>
            </div>
            <button
              onClick={handleExportJSON}
              disabled={entries.length === 0}
              className="px-3 py-1.5 rounded-lg text-xs font-medium bg-emerald-500/20 text-emerald-300 hover:bg-emerald-500/30 border border-emerald-500/30 transition-all flex items-center gap-1.5 disabled:opacity-40"
            >
              <Download className="w-3.5 h-3.5" />
              <span>JSON</span>
            </button>
          </div>
        </div>

        {/* Entries List */}
        <div className="flex-1 overflow-y-auto p-6 space-y-3">
          {entries.length === 0 ? (
            <div className="text-center py-12 text-stone-500 text-sm">
              No edit log records yet. Calibrate a trip and apply AI edits to accumulate training samples!
            </div>
          ) : (
            entries.map((entry) => (
              <div
                key={entry.id}
                className="p-4 rounded-xl bg-stone-950 border border-stone-800/80 space-y-2 text-xs"
              >
                <div className="flex items-center justify-between text-stone-400">
                  <span className="font-mono text-stone-300">Photo ID #{entry.photoId}</span>
                  <div className="flex items-center gap-2">
                    {entry.wasCorrected ? (
                      <span className="px-2 py-0.5 rounded-md bg-amber-500/20 text-amber-300 border border-amber-500/30 text-[10px] font-semibold">
                        Corrected
                      </span>
                    ) : (
                      <span className="px-2 py-0.5 rounded-md bg-stone-800 text-stone-400 text-[10px]">
                        Initial Accept
                      </span>
                    )}
                    <span className="text-[11px] text-stone-500">
                      {new Date(entry.timestampMillis).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </span>
                  </div>
                </div>

                <p className="text-stone-300">
                  <span className="text-stone-500">Style:</span> {entry.styleSummary}
                </p>

                {entry.correctionNote && (
                  <p className="text-amber-300/90 bg-amber-950/30 p-2 rounded-lg border border-amber-900/40">
                    <span className="font-semibold text-amber-400">Feedback:</span> "{entry.correctionNote}"
                  </p>
                )}
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 bg-stone-950 border-t border-stone-800 flex justify-between items-center text-xs text-stone-500">
          <span>Schema: EditLogRow (Room Entity compatible)</span>
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-stone-800 hover:bg-stone-700 text-stone-200 transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
