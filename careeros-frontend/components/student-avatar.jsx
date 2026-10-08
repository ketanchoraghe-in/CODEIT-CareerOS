"use client";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { useStudentPhoto } from "@/lib/profile-photo";
import { cn } from "cn";

function initialsOf(name, email) {
  const source = name || email || "U";
  return source
    .split(/[\s@._]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

/**
 * Student avatar that shows the uploaded profile photo when present and
 * falls back to initials otherwise. `photoKey` is the `profilePhotoUrl`
 * storage key from GET /students/me (truthy = photo exists).
 */
export function StudentAvatar({
  photoKey,
  photoUrl: photoUrlOverride,
  refreshKey = 0,
  fullName,
  email,
  initials,
  className,
  fallbackClassName,
}) {
  const fetchedUrl = useStudentPhoto(photoKey, refreshKey);
  const photoUrl = photoUrlOverride || fetchedUrl;
  const fallback = initials || initialsOf(fullName, email);

  return (
    <Avatar className={className}>
      {photoUrl && <AvatarImage src={photoUrl} alt={fullName ? `${fullName}'s profile photo` : "Profile photo"} />}
      <AvatarFallback className={cn("font-semibold", fallbackClassName)}>{fallback}</AvatarFallback>
    </Avatar>
  );
}
