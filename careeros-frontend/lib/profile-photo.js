import { useEffect, useState } from "react";
import { auth } from "@/lib/auth";
import api from "@/lib/api";

/**
 * Fetch the current student's profile photo as an object URL.
 * Returns null when no photo exists (404) or the user is not a student.
 * Goes through the shared API client so refresh/timeout/error handling
 * stays in one place. Callers must not cache across logout.
 */
export async function fetchStudentPhotoObjectUrl() {
  if (typeof window === "undefined") return null;
  if (!auth.getAccessToken()) return null;
  try {
    const blob = await api.get("/students/me/photo", { responseType: "blob" });
    if (!blob || blob.size === 0) return null;
    return URL.createObjectURL(blob);
  } catch {
    return null;
  }
}

/**
 * React hook: given the profile's `profilePhotoUrl` storage key (truthy when a
 * photo exists), load the authenticated photo blob into an object URL.
 * `refreshKey` forces a reload after upload/remove.
 */
export function useStudentPhoto(photoKey, refreshKey = 0) {
  const [photoUrl, setPhotoUrl] = useState(null);
  const [prevKey, setPrevKey] = useState(photoKey);

  // Reset during render when the key changes (the React-recommended
  // alternative to setState inside an effect) so a removed photo
  // never leaves a stale object URL behind.
  if (prevKey !== photoKey) {
    setPrevKey(photoKey);
    setPhotoUrl(null);
  }

  useEffect(() => {
    let cancelled = false;
    let objectUrl = null;
    if (!photoKey) {
      return undefined;
    }
    fetchStudentPhotoObjectUrl().then((url) => {
      if (cancelled) {
        if (url) URL.revokeObjectURL(url);
        return;
      }
      objectUrl = url;
      setPhotoUrl(url);
    });
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [photoKey, refreshKey]);

  return photoUrl;
}
