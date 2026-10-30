"use client";

import { Volume2, VolumeX } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Segmented, SegmentedItem } from "@/components/ui/segmented";
import { CheckoutDesk } from "@/features/desk/checkout-desk";
import { Kbd } from "@/features/desk/kbd";
import { ReturnsDesk } from "@/features/desk/returns-desk";
import { setMuted, useMuted } from "@/features/desk/sounds";
import { useShortcuts } from "@/features/desk/use-shortcuts";
import { usePathname, useRouter } from "@/i18n/navigation";

type Mode = "checkout" | "returns";

/** Every desk mode's scan box has this id, so a click anywhere can put the cursor back. */
const focusScanBox = () => document.getElementById("desk-scan")?.focus();

/**
 * Desk mode (checklist step 236): one big scan box, always ready. Checkout: scan the member's
 * card, then their books. Returns: scan books one after another. The mode is in the URL
 * (?mode=returns) so each desk computer can bookmark the one it uses.
 *
 * Shortcuts (step 241): F2 check out, F3 returns, F7 sound on/off; the checkout desk adds its own.
 */
export function DeskMode() {
  const t = useTranslations("Desk");
  const router = useRouter();
  const pathname = usePathname();
  const mode: Mode = useSearchParams().get("mode") === "returns" ? "returns" : "checkout";
  const muted = useMuted();
  const switchTo = (next: Mode) =>
    router.replace(next === "returns" ? `${pathname}?mode=returns` : pathname);

  useShortcuts({
    F2: () => switchTo("checkout"),
    F3: () => switchTo("returns"),
    F7: () => setMuted(!muted),
  });

  return (
    <div
      className="grid gap-6"
      // A click on empty space (not a button or link) puts the cursor back in the scan box
      onClick={(event) => {
        if (
          !(event.target as HTMLElement).closest("a, button, input, textarea, select, [role=tab]")
        ) {
          focusScanBox();
        }
      }}
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Segmented value={mode} label={t("modes")} onValueChange={(next) => switchTo(next as Mode)}>
          <SegmentedItem value="checkout">
            {t("checkout")} <Kbd>F2</Kbd>
          </SegmentedItem>
          <SegmentedItem value="returns">
            {t("returns")} <Kbd>F3</Kbd>
          </SegmentedItem>
        </Segmented>
        <Button variant="ghost" size="sm" aria-pressed={!muted} onClick={() => setMuted(!muted)}>
          {muted ? <VolumeX aria-hidden /> : <Volume2 aria-hidden />}
          {muted ? t("soundOff") : t("soundOn")} <Kbd>F7</Kbd>
        </Button>
      </div>

      {/* A fresh desk per mode: switching starts clean, with the scan box focused */}
      {mode === "checkout" ? <CheckoutDesk key="checkout" /> : <ReturnsDesk key="returns" />}

      <p className="text-xs text-muted-foreground">
        {t.rich("shortcutsHint", { kbd: (chunks) => <Kbd>{chunks}</Kbd> })}
      </p>
    </div>
  );
}
