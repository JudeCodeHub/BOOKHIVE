"use client";

import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { CheckoutDesk } from "@/features/desk/checkout-desk";
import { ReturnsDesk } from "@/features/desk/returns-desk";
import { usePathname, useRouter } from "@/i18n/navigation";

type Mode = "checkout" | "returns";

/** Every desk mode's scan box has this id, so a click anywhere can put the cursor back. */
const focusScanBox = () => document.getElementById("desk-scan")?.focus();

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
      <Tabs
        value={mode}
        onValueChange={(next) => {
          router.replace(next === "returns" ? `${pathname}?mode=returns` : pathname);
        }}
      >
        <TabsList aria-label={t("modes")}>
          <TabsTrigger value="checkout">{t("checkout")}</TabsTrigger>
          <TabsTrigger value="returns">{t("returns")}</TabsTrigger>
        </TabsList>
      </Tabs>

      {/* A fresh desk per mode: switching starts clean, with the scan box focused */}
      {mode === "checkout" ? <CheckoutDesk key="checkout" /> : <ReturnsDesk key="returns" />}
    </div>
  );
}
