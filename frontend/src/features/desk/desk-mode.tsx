"use client";

import { useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ScanInput, type ScanInputHandle } from "@/features/desk/scan-input";
import { usePathname, useRouter } from "@/i18n/navigation";

type Mode = "checkout" | "returns";

/**
 * Desk mode (checklist step 236): one big scan box, always ready. Checkout: scan the member's
 * card, then their books. Returns: scan books one after another. The mode is in the URL
 * (?mode=returns) so each desk computer can bookmark the one it uses.
 */
export function DeskMode() {
  const t = useTranslations("Desk");
  const router = useRouter();
  const pathname = usePathname();
  const mode: Mode = useSearchParams().get("mode") === "returns" ? "returns" : "checkout";
  const scanBox = useRef<ScanInputHandle>(null);
  const [lastScan, setLastScan] = useState<string | null>(null);

  return (
    <div
      className="grid gap-6"
      // A click on empty space (not a button or link) puts the cursor back in the scan box
      onClick={(event) => {
        if (
          !(event.target as HTMLElement).closest("a, button, input, textarea, select, [role=tab]")
        ) {
          scanBox.current?.focus();
        }
      }}
    >
      <Tabs
        value={mode}
        onValueChange={(next) => {
          setLastScan(null);
          router.replace(next === "returns" ? `${pathname}?mode=returns` : pathname);
          scanBox.current?.focus();
        }}
      >
        <TabsList aria-label={t("modes")}>
          <TabsTrigger value="checkout">{t("checkout")}</TabsTrigger>
          <TabsTrigger value="returns">{t("returns")}</TabsTrigger>
        </TabsList>
      </Tabs>

      <ScanInput
        // A fresh box per mode, focused right away
        key={mode}
        ref={scanBox}
        id="desk-scan"
        label={mode === "checkout" ? t("scanMemberLabel") : t("scanReturnLabel")}
        hint={mode === "checkout" ? t("scanMemberHint") : t("scanReturnHint")}
        placeholder={t("placeholder")}
        onScan={setLastScan}
        className="max-w-2xl"
      />

      {/* Screen readers hear each scan; steps 237-240 replace this with the real result */}
      <p role="status" aria-live="polite" className="text-sm text-muted-foreground">
        {lastScan && t("scanned", { code: lastScan })}
      </p>
    </div>
  );
}
