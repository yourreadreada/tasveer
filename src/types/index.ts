export interface Photo {
  id: string | number;
  uri: string;
  takenAtMillis: number;
  latitude?: number | null;
  longitude?: number | null;
  isFrontCamera?: boolean;
  title?: string;
  locationName?: string;
  folderName?: string;
}

export interface PhotoFolder {
  id: string;
  name: string;
  photos: Photo[];
  coverPhotoUri: string;
  photoCount: number;
}

export interface Trip {
  id: string;
  photos: Photo[];
  startMillis: number;
  endMillis: number;
  title?: string;
  locationName?: string;
}

export interface StyleDescription {
  summary: string;
  toneNotes: string;
  cropNotes: string;
  rawModelResponse: string;
}

export interface PhotoEditParams {
  photoId: string | number;
  exposureShift: number;      // -1.0 .. 1.0
  whiteBalanceShiftK: number; // kelvin shift e.g. -2000 .. +2000
  saturationShift: number;    // -1.0 .. 1.0
  shadowsLift: number;        // 0 .. 1.0
  contrastShift: number;      // -1.0 .. 1.0
  cropLeft: number;           // normalized 0..1 crop box
  cropTop: number;
  cropRight: number;
  cropBottom: number;
  blurBackground: boolean;
  reasoning: string;
}

export interface EditLogEntry {
  id: string | number;
  photoId: string | number;
  styleSummary: string;
  initialParams: string; // JSON serialized PhotoEditParams
  finalParams: string;   // JSON serialized PhotoEditParams
  wasCorrected: boolean;
  correctionNote?: string | null;
  timestampMillis: number;
}

export type AppScreen = 'gallery' | 'trip' | 'edit-reference' | 'batch-review';
