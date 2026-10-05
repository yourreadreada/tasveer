import React, { useState, useMemo } from 'react';
import {
  Folder,
  FolderOpen,
  Search,
  X,
  Image as ImageIcon,
  Upload,
  ArrowRight,
  Layers,
  Sparkles,
} from 'lucide-react';
import { Trip, Photo, PhotoFolder } from '../types';

interface GalleryScreenProps {
  trips: Trip[]; // Folders represented as trips for full backward-compatibility with downstream editing flows
  folders?: PhotoFolder[];
  onTripClick: (trip: Trip) => void;
  onUploadClick?: () => void;
}

export const GalleryScreen: React.FC<GalleryScreenProps> = ({
  trips,
  onTripClick,
  onUploadClick,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedFolderFilter, setSelectedFolderFilter] = useState<string>('all');
  const [viewMode, setViewMode] = useState<'folders' | 'stream'>('folders');

  // Extract all unique folder names
  const folderNames = useMemo(() => {
    return Array.from(new Set(trips.map((t) => t.locationName || t.title || 'Other')));
  }, [trips]);

  // Filter folders and their photos
  const filteredFolders = useMemo(() => {
    return trips.filter((folderTrip) => {
      const folderName = folderTrip.locationName || folderTrip.title || '';

      // 1. Folder filter pill
      if (selectedFolderFilter !== 'all' && folderName.toLowerCase() !== selectedFolderFilter.toLowerCase()) {
        return false;
      }

      // 2. Search query (matches folder name, photo titles, or locations)
      if (searchQuery.trim()) {
        const query = searchQuery.trim().toLowerCase();
        const matchesFolder = folderName.toLowerCase().includes(query);
        const matchesPhotos = folderTrip.photos.some(
          (p) =>
            p.title?.toLowerCase().includes(query) ||
            p.locationName?.toLowerCase().includes(query)
        );
        if (!matchesFolder && !matchesPhotos) {
          return false;
        }
      }

      return true;
    });
  }, [trips, selectedFolderFilter, searchQuery]);

  const totalPhotosCount = useMemo(() => {
    return trips.reduce((acc, curr) => acc + curr.photos.length, 0);
  }, [trips]);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
      {/* Header section - Google Photos Device Folders style */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
        <div>
          <div className="flex items-center gap-2 mb-1.5">
            <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-500/10 text-amber-400 border border-amber-500/20 flex items-center gap-1.5">
              <Folder className="w-3.5 h-3.5" />
              Device Folders
            </span>
            <span className="text-xs text-stone-500">
              {trips.length} folders • {totalPhotosCount} photos
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-stone-100">
            Photos on device
          </h1>
          <p className="text-sm text-stone-400 max-w-2xl mt-1">
            Organized folder-wise like Google Photos. Select any folder (Camera, Downloads, WhatsApp, etc.) to browse its photos and calibrate AI edits.
          </p>
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-2">
          {onUploadClick && (
            <button
              onClick={onUploadClick}
              className="px-4 py-2.5 rounded-xl text-sm font-medium bg-stone-900 hover:bg-stone-800 text-stone-200 border border-stone-800 flex items-center gap-2 transition-colors shadow-sm"
            >
              <Upload className="w-4 h-4 text-amber-400" />
              <span>Add Photos</span>
            </button>
          )}

          {/* View toggle (Folder Cards vs Folder Sections) */}
          <div className="flex items-center p-1 rounded-xl bg-stone-900 border border-stone-800">
            <button
              onClick={() => setViewMode('folders')}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                viewMode === 'folders'
                  ? 'bg-amber-500 text-stone-950 font-semibold shadow-sm'
                  : 'text-stone-400 hover:text-stone-200'
              }`}
            >
              Folder Grid
            </button>
            <button
              onClick={() => setViewMode('stream')}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                viewMode === 'stream'
                  ? 'bg-amber-500 text-stone-950 font-semibold shadow-sm'
                  : 'text-stone-400 hover:text-stone-200'
              }`}
            >
              Stream View
            </button>
          </div>
        </div>
      </div>

      {/* Search Toolbar & Folder Filter Pills */}
      <div className="mb-8 p-4 sm:p-5 rounded-2xl bg-stone-900/90 border border-stone-800 shadow-xl space-y-3.5">
        <div className="relative">
          <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-stone-500">
            <Search className="w-4 h-4" />
          </div>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search folders or photos (e.g. Camera, WhatsApp, Downloads)..."
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

        {/* Folder Quick Filters */}
        <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none text-xs">
          <span className="text-stone-500 text-[11px] font-medium shrink-0 flex items-center gap-1">
            <FolderOpen className="w-3.5 h-3.5" />
            Folder:
          </span>
          <button
            onClick={() => setSelectedFolderFilter('all')}
            className={`px-3 py-1 rounded-full text-xs font-medium shrink-0 transition-all ${
              selectedFolderFilter === 'all'
                ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40'
                : 'bg-stone-950 text-stone-400 hover:text-stone-200 border border-stone-800'
            }`}
          >
            All Folders ({trips.length})
          </button>
          {folderNames.map((name) => {
            const count = trips.find((t) => (t.locationName || t.title) === name)?.photos.length || 0;
            return (
              <button
                key={name}
                onClick={() => setSelectedFolderFilter(name)}
                className={`px-3 py-1 rounded-full text-xs font-medium shrink-0 transition-all flex items-center gap-1.5 ${
                  selectedFolderFilter.toLowerCase() === name.toLowerCase()
                    ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40'
                    : 'bg-stone-950 text-stone-400 hover:text-stone-200 border border-stone-800'
                }`}
              >
                <span>{name}</span>
                <span className="opacity-60 text-[10px]">({count})</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Main Content: Folder Grid vs Stream View */}
      {filteredFolders.length === 0 ? (
        <div className="rounded-2xl border border-stone-800 bg-stone-900/40 p-12 text-center">
          <ImageIcon className="w-12 h-12 text-stone-600 mx-auto mb-4" />
          <h3 className="text-lg font-medium text-stone-200">No folders match your search</h3>
          <p className="text-sm text-stone-400 mt-1 max-w-md mx-auto">
            {searchQuery
              ? `No folders or photos found matching "${searchQuery}". Try searching for another name.`
              : 'No folders available.'}
          </p>
          {searchQuery && (
            <button
              onClick={() => {
                setSearchQuery('');
                setSelectedFolderFilter('all');
              }}
              className="mt-4 px-4 py-2 rounded-xl text-xs font-medium bg-stone-800 hover:bg-stone-700 text-stone-200 transition-colors"
            >
              Reset Search
            </button>
          )}
        </div>
      ) : viewMode === 'folders' ? (
        /* GOOGLE PHOTOS DEVICE FOLDER CARDS GRID */
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {filteredFolders.map((folder) => {
            const folderTitle = folder.locationName || folder.title || 'Folder';
            const coverPhoto = folder.photos[0]?.uri || '';
            const previewPhotos = folder.photos.slice(0, 4);

            return (
              <div
                key={folder.id}
                onClick={() => onTripClick(folder)}
                className="group cursor-pointer rounded-2xl bg-stone-900/80 border border-stone-800 hover:border-amber-500/40 transition-all duration-300 hover:shadow-xl hover:shadow-black/50 overflow-hidden flex flex-col"
              >
                {/* Image Preview Container (Large Cover + Multi-thumbnail strip) */}
                <div className="relative aspect-4/3 overflow-hidden bg-stone-950">
                  <img
                    src={coverPhoto}
                    alt={folderTitle}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-stone-950 via-stone-950/20 to-transparent" />

                  {/* Folder Badge & Count */}
                  <div className="absolute top-3 left-3 flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-stone-950/80 backdrop-blur-md border border-stone-800/80 text-xs font-medium text-stone-200">
                    <Folder className="w-3.5 h-3.5 text-amber-400" />
                    <span>{folder.photos.length} photos</span>
                  </div>

                  {/* Bottom Folder Title Overlay */}
                  <div className="absolute bottom-3 left-3 right-3 flex items-end justify-between">
                    <div>
                      <h3 className="text-base font-bold text-white group-hover:text-amber-300 transition-colors drop-shadow-md">
                        {folderTitle}
                      </h3>
                      <p className="text-xs text-stone-300 drop-shadow">
                        Ready for AI editing
                      </p>
                    </div>
                    <div className="w-8 h-8 rounded-full bg-amber-500/90 group-hover:bg-amber-400 text-stone-950 flex items-center justify-center transition-colors shadow-md">
                      <ArrowRight className="w-4 h-4" />
                    </div>
                  </div>
                </div>

                {/* Mini Preview Strip */}
                {previewPhotos.length > 1 && (
                  <div className="p-3 bg-stone-950/50 border-t border-stone-800/60 flex items-center gap-1.5">
                    {previewPhotos.map((p, idx) => (
                      <div
                        key={p.id || idx}
                        className="h-10 flex-1 rounded-md overflow-hidden bg-stone-900 border border-stone-800"
                      >
                        <img src={p.uri} alt="" className="w-full h-full object-cover" />
                      </div>
                    ))}
                    {folder.photos.length > 4 && (
                      <div className="h-10 w-9 rounded-md bg-stone-900 border border-stone-800 flex items-center justify-center text-[10px] font-semibold text-stone-400">
                        +{folder.photos.length - 4}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      ) : (
        /* STREAM VIEW: All folders with horizontal photo strips */
        <div className="space-y-8">
          {filteredFolders.map((folder) => {
            const folderTitle = folder.locationName || folder.title || 'Folder';
            return (
              <div
                key={folder.id}
                className="p-5 rounded-2xl bg-stone-900/60 border border-stone-800"
              >
                {/* Folder Header */}
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-400">
                      <Folder className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="text-lg font-bold text-stone-100 flex items-center gap-2">
                        {folderTitle}
                        <span className="text-xs font-normal px-2 py-0.5 rounded-full bg-stone-800 text-stone-400">
                          {folder.photos.length} photos
                        </span>
                      </h3>
                      <p className="text-xs text-stone-400">
                        Select photos to calibrate style and batch apply
                      </p>
                    </div>
                  </div>

                  <button
                    onClick={() => onTripClick(folder)}
                    className="px-3.5 py-1.5 rounded-xl text-xs font-semibold bg-stone-800 hover:bg-stone-700 text-amber-300 border border-stone-700 transition-colors flex items-center gap-1.5"
                  >
                    <span>Open Folder</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>

                {/* Photos Grid within Folder */}
                <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-6 gap-3">
                  {folder.photos.map((photo) => (
                    <div
                      key={photo.id}
                      onClick={() => onTripClick(folder)}
                      className="group cursor-pointer rounded-xl overflow-hidden aspect-square bg-stone-950 border border-stone-800 relative hover:border-amber-500/50 transition-all"
                    >
                      <img
                        src={photo.uri}
                        alt={photo.title || ''}
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                      />
                      <div className="absolute inset-0 bg-stone-950/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                        <span className="px-2 py-1 rounded bg-stone-950/80 text-[10px] text-amber-300 font-medium">
                          Select
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
