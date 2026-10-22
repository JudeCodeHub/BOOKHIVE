"use client";

import { useId, type ReactNode } from "react";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

/** Props to spread on the input so it's linked to its label, hint and error. */
export type FieldControlProps = {
  id: string;
  "aria-invalid"?: true;
  "aria-describedby"?: string;
};

/**
 * Label + input + hint + error, wired together for screen readers:
 *
 *   <FormField label="Email" error={errors.email?.message} required>
 *     {(field) => <Input type="email" {...field} {...register("email")} />}
 *   </FormField>
 *
 * The input gets an id (so clicking the label focuses it), aria-describedby pointing at the hint
 * and error (read out when the input is focused), and aria-invalid when there's an error.
 */
export function FormField({
  label,
  hint,
  error,
  required = false,
  className,
  children,
}: {
  label: ReactNode;
  hint?: ReactNode;
  error?: string;
  required?: boolean;
  className?: string;
  children: (field: FieldControlProps) => ReactNode;
}) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint && hintId, error && errorId].filter(Boolean).join(" ");

  return (
    <div className={cn("grid gap-1.5", className)}>
      <Label htmlFor={id}>
        {label}
        {required && (
          <>
            <span aria-hidden className="text-destructive">
              *
            </span>
            <span className="sr-only">(required)</span>
          </>
        )}
      </Label>
      {children({
        id,
        "aria-invalid": error ? true : undefined,
        "aria-describedby": describedBy || undefined,
      })}
      {hint && (
        <p id={hintId} className="text-sm text-muted-foreground">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-sm font-medium text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
