import { Photo, Trip } from '../types';

/**
 * Groups photos into "trips" automatically, per the original design:
 * primarily by date gaps (maxGapHours), refined by location when GPS is available (maxLocationDriftKm),
 * falling back gracefully to date-only clustering when it isn't.
 */
export class TripClusterer {
  private maxGapHours: number;
  private maxLocationDriftKm: number;

  constructor(maxGapHours = 18, maxLocationDriftKm = 50.0) {
    this.maxGapHours = maxGapHours;
    this.maxLocationDriftKm = maxLocationDriftKm;
  }

  public cluster(photos: Photo[]): Trip[] {
    if (!photos || photos.length === 0) return [];

    const sorted = [...photos].sort((a, b) => a.takenAtMillis - b.takenAtMillis);
    const trips: Photo[][] = [[sorted[0]]];

    for (let i = 1; i < sorted.length; i++) {
      const prev = sorted[i - 1];
      const curr = sorted[i];

      const gapHours = (curr.takenAtMillis - prev.takenAtMillis) / 3_600_000.0;
      const tooFarInTime = gapHours > this.maxGapHours;

      let tooFarInSpace = false;
      if (
        prev.latitude != null && prev.longitude != null &&
        curr.latitude != null && curr.longitude != null
      ) {
        tooFarInSpace = this.haversineKm(
          prev.latitude, prev.longitude,
          curr.latitude, curr.longitude
        ) > this.maxLocationDriftKm;
      }

      if (tooFarInTime || tooFarInSpace) {
        trips.push([curr]);
      } else {
        trips[trips.length - 1].push(curr);
      }
    }

    return trips.map((group, index) => {
      const startMillis = Math.min(...group.map(p => p.takenAtMillis));
      const endMillis = Math.max(...group.map(p => p.takenAtMillis));
      
      // Derive a human friendly location title if available from photo metadata
      const location = group.find(p => p.locationName)?.locationName || 
        (group.find(p => p.latitude != null) ? `Trip #${index + 1}` : `Collection ${index + 1}`);

      return {
        id: `trip_${index}_${startMillis}`,
        photos: group,
        startMillis,
        endMillis,
        title: location,
        locationName: location,
      };
    }).reverse(); // Newest trips first
  }

  private haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
    const r = 6371.0;
    const dLat = this.toRadians(lat2 - lat1);
    const dLon = this.toRadians(lon2 - lon1);
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(this.toRadians(lat1)) * Math.cos(this.toRadians(lat2)) *
      Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return r * c;
  }

  private toRadians(degrees: number): number {
    return (degrees * Math.PI) / 180;
  }
}
