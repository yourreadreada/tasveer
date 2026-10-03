import React, { useState, useMemo } from 'react';
import {
  Calendar,
  MapPin,
  Search,
  X,
  Filter,
  RotateCcw,
  Image as ImageIcon,
  CalendarRange,
} from 'lucide-react';
import { Trip } from '../types';

interface GalleryScreenProps {
  trips: Trip[];
  onTripClick: (trip: Trip) => void;
}

export const GalleryScreen: React.FC<GalleryScreenProps> = ({ trips, onTripClick }) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  // Format date range helper
  const formatDateRange = (start: number, end: number) => {
    const sDate = new Date(start);
    const eDate = new Date(end);
    const sStr = sDate.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
    const eStr = eDate.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
    return sStr === eStr.slice(0, sStr.length) ? eStr : `${sStr} – ${eStr}`;
  };

  // Filter trips based on location name and start date
  const filteredTrips = useMemo(() => {
    return trips.filter((trip) => {
      // 1. Search by location name (also checks title and photo location names)
      if (searchQuery.trim()) {
        const query = searchQuery.trim().toLowerCase();
        const matchesLocation = trip.locationName?.toLowerCase().includes(query);
        const matchesTitle = trip.title?.toLowerCase().includes(query);
        const matchesPhotoLocations = trip.photos.some((p) =>
          p.locationName?.toLowerCase().includes(query)
        );

        if (!matchesLocation && !matchesTitle && !matchesPhotoLocations) {
          return false;
        }
      }

      // 2. Filter based on trip start date
      if (startDate) {
        // Start of selected day in local time
        const startFilterMillis = new Date(`${startDate}T00:00:00`).getTime();
        if (trip.startMillis < startFilterMillis) {
          return false;
        }
      }

      if (endDate) {
        // End of selected day in local time
        const endFilterMillis = new Date(`${endDate}T23:59:59.999`).getTime();
        if (trip.startMillis > endFilterMillis) {
          return false;
        }
      }

      return true;
    });
  }, [trips, searchQuery, startDate, endDate]);

  const hasActiveFilters = Boolean(searchQuery.trim() || startDate || endDate);

  const handleClearFilters = () => {
    setSearchQuery('');
    setStartDate('');
    setEndDate('');
  };

  // Quick date presets
  const handleSetYearPreset = (year: number) => {
    setStartDate(`${year}-01-01`);
    setEndDate(`${year}-12-31`);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
      {/* Header section */}
      <div className="mb-6">
        <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-stone-100 mb-2">
          Your Clustered Trips
        </h1>
        <p className="text-sm text-stone-400 max-w-2xl">
          Tasveer automatically clusters photos by time gaps (≥18 hours) and GPS drift (≥50km).
          Search by location and filter by start date to organize and calibrate your photo sets.
        </p>
      </div>

      {/* Filter Toolbar: Search by Location & Date Range Picker */}
      <div className="mb-8 p-4 sm:p-5 rounded-2xl bg-stone-900/90 border border-stone-800 shadow-xl space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-12 gap-4 items-center">
          {/* Location Search Input */}
          <div className="md:col-span-6 relative">
            <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-stone-500">
              <Search className="w-4 h-4" />
            </div>
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search trips by location (e.g. Kyoto, Zermatt, Big Sur)..."
              className="w-full pl-10 pr-9 py-2.5 rounded-xl bg-stone-950 border border-stone-800 text-stone-100 text-sm placeholder-stone-500 focus:outline-none focus:border-amber-500 focus:ring-1 focus:ring-amber-500 transition-all"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-stone-500 hover:text-stone-300"
                title="Clear search"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          {/* Date Range Picker (Start Date to End Date) */}
          <div className="md:col-span-6 flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
            <div className="flex-1 flex items-center gap-2 bg-stone-950 px-3 py-2 rounded-xl border border-stone-800">
              <CalendarRange className="w-4 h-4 text-amber-500/80 shrink-0" />
              <div className="flex items-center gap-1.5 w-full text-xs">
                <span className="text-stone-400 font-medium">From:</span>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="bg-transparent text-stone-200 text-xs focus:outline-none w-full [color-scheme:dark]"
                  title="Filter trips starting on or after this date"
                />
              </div>
            </div>

            <div className="flex-1 flex items-center gap-2 bg-stone-950 px-3 py-2 rounded-xl border border-stone-800">
              <div className="flex items-center gap-1.5 w-full text-xs">
                <span className="text-stone-400 font-medium">To:</span>
                <input
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  className="bg-transparent text-stone-200 text-xs focus:outline-none w-full [color-scheme:dark]"
                  title="Filter trips starting on or before this date"
                />
              </div>
            </div>

            {hasActiveFilters && (
              <button
                onClick={handleClearFilters}
                className="px-3 py-2 rounded-xl bg-stone-800 hover:bg-stone-700 text-stone-300 text-xs font-medium transition-colors flex items-center justify-center gap-1.5 shrink-0 border border-stone-700/60"
                title="Reset all filters"
              >
                <RotateCcw className="w-3.5 h-3.5 text-amber-400" />
                <span className="hidden sm:inline">Reset</span>
              </button>
            )}
          </div>
        </div>

        {/* Filter Badges & Quick Presets */}
        <div className="pt-2 border-t border-stone-800/80 flex flex-wrap items-center justify-between gap-2 text-xs">
          <div className="flex items-center gap-2 text-stone-400">
            <span className="font-medium text-stone-300">
              Showing {filteredTrips.length} of {trips.length} trips
            </span>
            {hasActiveFilters && (
              <span className="px-2 py-0.5 rounded-full bg-amber-500/10 text-amber-400 border border-amber-500/20 text-[11px]">
                Filtered
              </span>
            )}
          </div>

          <div className="flex items-center gap-1.5 text-stone-400">
            <span className="text-[11px] text-stone-500">Quick dates:</span>
            <button
              onClick={() => handleSetYearPreset(2024)}
              className="px-2 py-0.5 rounded-md bg-stone-800 hover:bg-stone-700 text-stone-300 text-[11px] transition-colors"
            >
              2024
            </button>
            <button
              onClick={() => {
                setStartDate('2024-04-01');
                setEndDate('2024-05-31');
              }}
              className="px-2 py-0.5 rounded-md bg-stone-800 hover:bg-stone-700 text-stone-300 text-[11px] transition-colors"
            >
              Spring 2024
            </button>
            <button
              onClick={() => {
                setStartDate('2024-06-01');
                setEndDate('2024-08-31');
              }}
              className="px-2 py-0.5 rounded-md bg-stone-800 hover:bg-stone-700 text-stone-300 text-[11px] transition-colors"
            >
              Summer 2024
            </button>
          </div>
        </div>
      </div>

      {/* Trips Grid or Empty Filter State */}
      {filteredTrips.length === 0 ? (
        <div className="rounded-2xl border border-stone-800 bg-stone-900/40 p-12 text-center">
          <ImageIcon className="w-12 h-12 text-stone-600 mx-auto mb-4" />
          <h3 className="text-lg font-medium text-stone-200">No trips match your filters</h3>
          <p className="text-sm text-stone-400 mt-1 max-w-md mx-auto">
            {searchQuery && startDate
              ? `No trips found in "${searchQuery}" starting between ${startDate} and ${endDate || 'present'}.`
              : searchQuery
              ? `No trips found matching location "${searchQuery}".`
              : `No trips found matching the selected date range.`}
          </p>
          {hasActiveFilters && (
            <button
              onClick={handleClearFilters}
              className="mt-4 px-4 py-2 rounded-xl text-xs font-semibold bg-amber-500 hover:bg-amber-400 text-stone-950 transition-all inline-flex items-center gap-1.5"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span>Clear Search & Date Filters</span>
            </button>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredTrips.map((trip) => {
            const coverPhoto = trip.photos[0];
            return (
              <div
                key={trip.id}
                onClick={() => onTripClick(trip)}
                className="group relative bg-stone-900 rounded-2xl overflow-hidden border border-stone-800/80 hover:border-amber-500/50 hover:shadow-xl hover:shadow-amber-950/20 transition-all duration-300 cursor-pointer flex flex-col"
              >
                {/* Thumbnail container */}
                <div className="relative aspect-[16/10] overflow-hidden bg-stone-950">
                  {coverPhoto ? (
                    <img
                      src={coverPhoto.uri}
                      alt={trip.title || 'Trip photo'}
                      className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
                      loading="lazy"
                    />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center text-stone-700">
                      No Preview
                    </div>
                  )}

                  {/* Photo count badge */}
                  <div className="absolute top-3 right-3 px-2.5 py-1 rounded-full bg-stone-950/70 backdrop-blur-md text-xs font-semibold text-stone-200 border border-stone-800">
                    {trip.photos.length} photos
                  </div>

                  {/* Gradient overlay */}
                  <div className="absolute inset-0 bg-gradient-to-t from-stone-950 via-stone-950/20 to-transparent opacity-80" />

                  {/* Trip Title Overlay */}
                  <div className="absolute bottom-3 left-3 right-3">
                    <h3 className="text-lg font-bold text-stone-100 group-hover:text-amber-300 transition-colors truncate">
                      {trip.title || 'Trip'}
                    </h3>
                  </div>
                </div>

                {/* Details Footer */}
                <div className="p-4 flex flex-col justify-between flex-1 gap-3">
                  <div className="space-y-1.5 text-xs text-stone-400">
                    <div className="flex items-center gap-1.5">
                      <Calendar className="w-3.5 h-3.5 text-amber-500/80" />
                      <span>{formatDateRange(trip.startMillis, trip.endMillis)}</span>
                    </div>
                    {trip.locationName && (
                      <div className="flex items-center gap-1.5">
                        <MapPin className="w-3.5 h-3.5 text-orange-400/80" />
                        <span className="truncate">{trip.locationName}</span>
                      </div>
                    )}
                  </div>

                  <div className="pt-2 border-t border-stone-800 flex items-center justify-between text-xs">
                    <span className="text-stone-500">Preview-first AI pipeline</span>
                    <span className="text-amber-400 font-medium group-hover:translate-x-0.5 transition-transform flex items-center gap-1">
                      Edit Look →
                    </span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
