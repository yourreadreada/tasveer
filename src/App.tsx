import React, { useState, useEffect, useMemo, useRef } from 'react';
import { Navbar } from './components/Navbar';
import { GalleryScreen } from './components/GalleryScreen';
import { TripScreen } from './components/TripScreen';
import { EditScreen } from './components/EditScreen';
import { BatchReviewScreen } from './components/BatchReviewScreen';
import { DatasetModal } from './components/DatasetModal';
import { INITIAL_PHOTOS } from './utils/sampleData';
import { TripClusterer } from './utils/tripClusterer';
import { Photo, Trip, AppScreen, PhotoEditParams, StyleDescription, EditLogEntry } from './types';

export const App: React.FC = () => {
  const [photos, setPhotos] = useState<Photo[]>(INITIAL_PHOTOS);
  const [currentScreen, setCurrentScreen] = useState<AppScreen>('gallery');
  const [selectedTrip, setSelectedTrip] = useState<Trip | null>(null);
  const [referencePhotos, setReferencePhotos] = useState<Photo[]>([]);
  const [referenceParams, setReferenceParams] = useState<PhotoEditParams | null>(null);
  const [styleDescription, setStyleDescription] = useState<StyleDescription | null>(null);
  const [isAnalyzingStyle, setIsAnalyzingStyle] = useState<boolean>(false);
  const [datasetEntries, setDatasetEntries] = useState<EditLogEntry[]>([]);
  const [isDatasetOpen, setIsDatasetOpen] = useState<boolean>(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // Cluster photos into trips automatically (TripClusterer algorithm)
  const trips = useMemo(() => {
    const clusterer = new TripClusterer(18, 50.0);
    return clusterer.cluster(photos);
  }, [photos]);

  // Load existing edit logs from backend on mount
  useEffect(() => {
    fetch('/api/edit-log')
      .then((res) => res.json())
      .then((data) => {
        if (Array.isArray(data)) setDatasetEntries(data);
      })
      .catch((err) => console.warn('Could not fetch dataset log:', err));
  }, []);

  // Handle uploading user's own photos
  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    const newPhotos: Photo[] = [];
    const now = Date.now();

    Array.from(files).forEach((file, index) => {
      const url = URL.createObjectURL(file);
      newPhotos.push({
        id: `custom_${now}_${index}`,
        uri: url,
        title: file.name.replace(/\.[^/.]+$/, ''),
        locationName: 'Local Uploads',
        takenAtMillis: file.lastModified || now - index * 3600000,
        isFrontCamera: false,
      });
    });

    setPhotos((prev) => [...newPhotos, ...prev]);
    setCurrentScreen('gallery');
  };

  // Step 1: Open a trip
  const handleSelectTrip = (trip: Trip) => {
    setSelectedTrip(trip);
    setCurrentScreen('trip');
  };

  // Step 2: Choose 1-2 reference photos from the trip
  const handleReferenceChosen = (chosen: Photo[]) => {
    setReferencePhotos(chosen);
    setCurrentScreen('edit-reference');
  };

  // Step 3: Calibrate look and send to VLM for style analysis
  const handleConfirmLook = async (params: PhotoEditParams, activePhoto: Photo) => {
    setReferenceParams(params);
    setIsAnalyzingStyle(true);

    try {
      const res = await fetch('/api/vlm/analyze-style', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          referenceParams: params,
          sampleTitle: activePhoto.title || 'Trip reference photo',
        }),
      });

      if (res.ok) {
        const style: StyleDescription = await res.json();
        setStyleDescription(style);
        setCurrentScreen('batch-review');
      } else {
        throw new Error('Analysis request failed');
      }
    } catch (err) {
      console.error('Failed to analyze style:', err);
      // Fallback style
      const fallbackStyle: StyleDescription = {
        summary: 'Warm emotive grading with open midtones and balanced saturation',
        toneNotes: 'Balanced highlights and gentle shadows',
        cropNotes: 'Subject-focused framing',
        rawModelResponse: 'Fallback style definition',
      };
      setStyleDescription(fallbackStyle);
      setCurrentScreen('batch-review');
    } finally {
      setIsAnalyzingStyle(false);
    }
  };

  // Log an edit to the dataset (Room DB equivalent)
  const handleLogEdit = (
    photoId: string | number,
    styleSummary: string,
    initialParams: PhotoEditParams,
    finalParams: PhotoEditParams,
    wasCorrected: boolean,
    correctionNote?: string
  ) => {
    const entry: EditLogEntry = {
      id: `log_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`,
      photoId,
      styleSummary,
      initialParams: JSON.stringify(initialParams),
      finalParams: JSON.stringify(finalParams),
      wasCorrected,
      correctionNote: correctionNote || null,
      timestampMillis: Date.now(),
    };

    setDatasetEntries((prev) => [entry, ...prev]);

    // Persist to backend server
    fetch('/api/edit-log', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(entry),
    }).catch((err) => console.warn('Failed to persist edit log:', err));
  };

  return (
    <div className="min-h-screen bg-stone-950 text-stone-100 flex flex-col">
      {/* Hidden file input */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept="image/*"
        className="hidden"
        onChange={handleFileUpload}
      />

      {/* Global Navigation */}
      <Navbar
        currentScreen={currentScreen}
        onNavigate={(screen) => setCurrentScreen(screen)}
        datasetCount={datasetEntries.length}
        onOpenDataset={() => setIsDatasetOpen(true)}
        onUploadPhotos={() => fileInputRef.current?.click()}
      />

      {/* Main Content Area */}
      <main className="flex-1">
        {currentScreen === 'gallery' && (
          <GalleryScreen trips={trips} onTripClick={handleSelectTrip} />
        )}

        {currentScreen === 'trip' && selectedTrip && (
          <TripScreen
            trip={selectedTrip}
            onBack={() => setCurrentScreen('gallery')}
            onReferenceChosen={handleReferenceChosen}
          />
        )}

        {currentScreen === 'edit-reference' && referencePhotos.length > 0 && (
          <EditScreen
            referencePhotos={referencePhotos}
            onBack={() => setCurrentScreen('trip')}
            onConfirmLook={handleConfirmLook}
            isAnalyzing={isAnalyzingStyle}
          />
        )}

        {currentScreen === 'batch-review' &&
          selectedTrip &&
          referenceParams &&
          styleDescription &&
          referencePhotos.length > 0 && (
            <BatchReviewScreen
              trip={selectedTrip}
              style={styleDescription}
              referenceParams={referenceParams}
              referencePhoto={referencePhotos[0]}
              onBackToTrips={() => setCurrentScreen('gallery')}
              onLogEdit={handleLogEdit}
            />
          )}
      </main>

      {/* Dataset Drawer / Modal */}
      <DatasetModal
        isOpen={isDatasetOpen}
        onClose={() => setIsDatasetOpen(false)}
        entries={datasetEntries}
      />
    </div>
  );
};
