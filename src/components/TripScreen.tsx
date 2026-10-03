import React, { useState } from 'react';
import { ArrowLeft, Check, Sparkles, Sliders, Info } from 'lucide-react';
import { Photo, Trip } from '../types';

interface TripScreenProps {
  trip: Trip;
  onBack: () => void;
  onReferenceChosen: (selectedPhotos: Photo[]) => void;
}

export const TripScreen: React.FC<TripScreenProps> = ({
  trip,
  onBack,
  onReferenceChosen,
}) => {
  const [selectedPhotos, setSelectedPhotos] = useState<Photo[]>(() => {
    // Default to the first photo if available
    return trip.photos.length > 0 ? [trip.photos[0]] : [];
  });

  const toggleSelect = (photo: Photo) => {
    const isSelected = selectedPhotos.some((p) => p.id === photo.id);
    if (isSelected) {
      if (selectedPhotos.length > 1) {
        setSelectedPhotos(selectedPhotos.filter((p) => p.id !== photo.id));
      }
    } else {
      if (selectedPhotos.length < 2) {
        setSelectedPhotos([...selectedPhotos, photo]);
      } else {
        // Replace second photo
        setSelectedPhotos([selectedPhotos[0], photo]);
      }
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
      {/* Back button & Title */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="p-2 rounded-xl bg-stone-900 hover:bg-stone-800 text-stone-300 border border-stone-800 transition-colors"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-stone-100">{trip.title || 'Trip Photos'}</h1>
            <p className="text-xs text-stone-400">
              {trip.photos.length} photos • {trip.locationName}
            </p>
          </div>
        </div>

        {/* Action Button */}
        <button
          onClick={() => onReferenceChosen(selectedPhotos)}
          disabled={selectedPhotos.length === 0}
          className="px-5 py-2.5 rounded-xl font-semibold text-sm bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-400 hover:to-orange-400 text-stone-950 shadow-lg shadow-amber-950/40 disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center justify-center gap-2"
        >
          <Sliders className="w-4 h-4" />
          <span>Calibrate Look ({selectedPhotos.length}/2 selected) →</span>
        </button>
      </div>

      {/* Guide Banner */}
      <div className="p-4 mb-6 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-start gap-3 text-amber-200 text-xs sm:text-sm">
        <Info className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
        <div>
          <span className="font-semibold text-amber-300">Preview-First Calibration:</span> Select 1 or 2 representative photos below to dial in the look. VLM will extract the semantic style description and reason out bespoke adjustments for the remaining {Math.max(0, trip.photos.length - selectedPhotos.length)} photos in this trip.
        </div>
      </div>

      {/* Photos Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
        {trip.photos.map((photo) => {
          const isSelected = selectedPhotos.some((p) => p.id === photo.id);
          const selectionIndex = selectedPhotos.findIndex((p) => p.id === photo.id);

          return (
            <div
              key={photo.id}
              onClick={() => toggleSelect(photo)}
              className={`group relative aspect-square rounded-xl overflow-hidden bg-stone-900 border-2 cursor-pointer transition-all ${
                isSelected
                  ? 'border-amber-400 ring-4 ring-amber-500/20 scale-[0.98]'
                  : 'border-stone-800 hover:border-stone-700'
              }`}
            >
              <img
                src={photo.uri}
                alt={photo.title || 'Trip photo'}
                className="w-full h-full object-cover"
                loading="lazy"
              />

              {/* Selection Checkbox / Badge */}
              <div
                className={`absolute top-2.5 right-2.5 w-7 h-7 rounded-full flex items-center justify-center transition-all ${
                  isSelected
                    ? 'bg-amber-400 text-stone-950 shadow-md font-bold text-xs'
                    : 'bg-stone-950/60 text-stone-400 border border-white/20 group-hover:bg-stone-900'
                }`}
              >
                {isSelected ? (
                  <span>#{selectionIndex + 1}</span>
                ) : (
                  <Check className="w-3.5 h-3.5 opacity-0 group-hover:opacity-100" />
                )}
              </div>

              {/* Caption on hover */}
              <div className="absolute inset-x-0 bottom-0 p-2 bg-gradient-to-t from-stone-950/90 to-transparent text-[11px] text-stone-300 truncate">
                {photo.title || new Date(photo.takenAtMillis).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
