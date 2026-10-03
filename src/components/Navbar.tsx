import React from 'react';
import { Camera, Database, Sparkles, Plus, Image as ImageIcon } from 'lucide-react';
import { AppScreen } from '../types';

interface NavbarProps {
  currentScreen: AppScreen;
  onNavigate: (screen: AppScreen) => void;
  datasetCount: number;
  onOpenDataset: () => void;
  onUploadPhotos: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  currentScreen,
  onNavigate,
  datasetCount,
  onOpenDataset,
  onUploadPhotos,
}) => {
  return (
    <header className="sticky top-0 z-40 bg-stone-900/90 backdrop-blur-md border-b border-stone-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
        {/* Brand */}
        <div className="flex items-center gap-3 cursor-pointer" onClick={() => onNavigate('gallery')}>
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-amber-600 via-orange-500 to-amber-400 flex items-center justify-center shadow-lg shadow-amber-900/20">
            <Camera className="w-5 h-5 text-stone-950 font-bold" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-bold text-lg text-stone-100 tracking-tight">Tasveer</span>
              <span className="text-[10px] uppercase tracking-wider px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300 font-semibold border border-amber-500/30">
                VLM Editor
              </span>
            </div>
            <p className="text-xs text-stone-400 hidden sm:block">Per-photo AI calibration & trip clustering</p>
          </div>
        </div>

        {/* Navigation & Controls */}
        <div className="flex items-center gap-2 sm:gap-4">
          <button
            onClick={() => onNavigate('gallery')}
            className={`px-3 py-1.5 rounded-lg text-xs sm:text-sm font-medium transition-colors flex items-center gap-1.5 ${
              currentScreen === 'gallery'
                ? 'bg-stone-800 text-stone-100'
                : 'text-stone-400 hover:text-stone-200 hover:bg-stone-800/50'
            }`}
          >
            <ImageIcon className="w-4 h-4" />
            <span>Trips</span>
          </button>

          <button
            onClick={onUploadPhotos}
            className="px-3 py-1.5 rounded-lg text-xs sm:text-sm font-medium bg-stone-800 hover:bg-stone-700 text-stone-200 transition-colors flex items-center gap-1.5 border border-stone-700"
          >
            <Plus className="w-4 h-4 text-amber-400" />
            <span className="hidden sm:inline">Add Photos</span>
          </button>

          {/* Dataset logging status badge (Path A -> Path B bridge) */}
          <button
            onClick={onOpenDataset}
            className="px-3 py-1.5 rounded-lg text-xs sm:text-sm font-medium bg-stone-900 hover:bg-stone-800 text-stone-300 transition-all flex items-center gap-2 border border-stone-700/60"
            title="Logged dataset for future model training"
          >
            <Database className="w-3.5 h-3.5 text-emerald-400" />
            <span className="hidden sm:inline">Training Log:</span>
            <span className="inline-flex items-center justify-center px-1.5 py-0.2 rounded-full text-xs font-mono bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              {datasetCount}
            </span>
          </button>
        </div>
      </div>
    </header>
  );
};
