import { Photo } from '../types';

// High quality curated Unsplash photography samples formatted for photography editing
export const INITIAL_PHOTOS: Photo[] = [
  // --- TRIP 1: Kyoto & Arashiyama (Japan) ---
  {
    id: 101,
    uri: 'https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?auto=format&fit=crop&w=1200&q=80',
    title: 'Arashiyama Bamboo Grove at Dawn',
    locationName: 'Kyoto, Japan',
    takenAtMillis: 1713506400000, // April 19, 2024 06:00:00
    latitude: 35.0166,
    longitude: 135.6713,
    isFrontCamera: false,
  },
  {
    id: 102,
    uri: 'https://images.unsplash.com/photo-1503899036084-c55cdd92da26?auto=format&fit=crop&w=1200&q=80',
    title: 'Gion Traditional Street Lanterns',
    locationName: 'Kyoto, Japan',
    takenAtMillis: 1713524400000, // April 19, 2024 11:00:00 (5 hrs later)
    latitude: 35.0037,
    longitude: 135.7772,
    isFrontCamera: false,
  },
  {
    id: 103,
    uri: 'https://images.unsplash.com/photo-1528164344705-475426879c0d?auto=format&fit=crop&w=1200&q=80',
    title: 'Fushimi Inari Torii Path',
    locationName: 'Kyoto, Japan',
    takenAtMillis: 1713542400000, // April 19, 2024 16:00:00 (5 hrs later)
    latitude: 34.9671,
    longitude: 135.7727,
    isFrontCamera: false,
  },
  {
    id: 104,
    uri: 'https://images.unsplash.com/photo-1545569341-9eb8b30979d9?auto=format&fit=crop&w=1200&q=80',
    title: 'Kiyomizu-dera Evening Glow',
    locationName: 'Kyoto, Japan',
    takenAtMillis: 1713553200000, // April 19, 2024 19:00:00 (3 hrs later)
    latitude: 34.9949,
    longitude: 135.7850,
    isFrontCamera: false,
  },

  // --- TRIP 2: Swiss Alpine Crossing (Switzerland) (Date gap > 2 weeks, location drift > 9000km) ---
  {
    id: 201,
    uri: 'https://images.unsplash.com/photo-1530122037265-a5f1f91d3b99?auto=format&fit=crop&w=1200&q=80',
    title: 'Matterhorn Alpine Sunrise',
    locationName: 'Zermatt, Switzerland',
    takenAtMillis: 1715497200000, // May 12, 2024 07:00:00
    latitude: 45.9763,
    longitude: 7.7491,
    isFrontCamera: false,
  },
  {
    id: 202,
    uri: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=1200&q=80',
    title: 'Glacier Lake Reflections',
    locationName: 'Zermatt, Switzerland',
    takenAtMillis: 1715515200000, // May 12, 2024 12:00:00 (5 hrs later)
    latitude: 45.9920,
    longitude: 7.7310,
    isFrontCamera: false,
  },
  {
    id: 203,
    uri: 'https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=1200&q=80',
    title: 'Mountain Ridge Cloud Inversion',
    locationName: 'Zermatt, Switzerland',
    takenAtMillis: 1715536800000, // May 12, 2024 18:00:00 (6 hrs later)
    latitude: 46.0125,
    longitude: 7.7600,
    isFrontCamera: false,
  },

  // --- TRIP 3: Big Sur & California Coast (USA) (Date gap > 1 month) ---
  {
    id: 301,
    uri: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1200&q=80',
    title: 'Bixby Bridge & Coastal Mist',
    locationName: 'Big Sur, California',
    takenAtMillis: 1718870400000, // June 20, 2024 08:00:00
    latitude: 36.3714,
    longitude: -121.9018,
    isFrontCamera: false,
  },
  {
    id: 302,
    uri: 'https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=1200&q=80',
    title: 'Pacific Ocean Sunset Spray',
    locationName: 'Big Sur, California',
    takenAtMillis: 1718910000000, // June 20, 2024 19:00:00 (11 hrs later)
    latitude: 36.2704,
    longitude: -121.8081,
    isFrontCamera: false,
  },
  {
    id: 303,
    uri: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=1200&q=80',
    title: 'Yosemite Valley Stream',
    locationName: 'Yosemite, California',
    takenAtMillis: 1718924400000, // June 20, 2024 23:00:00
    latitude: 37.7456,
    longitude: -119.5936,
    isFrontCamera: false,
  },
];
