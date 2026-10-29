"use client";

import { useQuery } from "@tanstack/react-query";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { useMoney } from "@/lib/formats";
import { api, type Schema } from "@/lib/api";

type Rules = Schema<"RulesResponse">;
type MembershipType = Schema<"MembershipTypeResponse">;

/**
 * The rules page (checklist step 208), built from what the system actually enforces: the
 * membership types (limits per type) and the library-wide rules (fines, holds...). When staff
 * change them, this page changes too: no separate text to keep in sync.
 */
export function RulesContent() {
  const t = useTranslations("Rules");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const locale = useLocale();
  const errorMessage = useAuthErrorMessage();
  const money = useMoney();
  const rules = useQuery({
    queryKey: ["library", "rules"],
    queryFn: () => api.get<Rules>("/library/rules"),
  });
  const types = useQuery({
    queryKey: ["membership-types"],
    queryFn: () => api.get<MembershipType[]>("/memberships/types"),
  });

  if (rules.isPending || types.isPending) return <HexLoader label={tCommon("loading")} />;
  if (rules.isError) return <FormAlert>{errorMessage(rules.error)}</FormAlert>;
  if (types.isError) return <FormAlert>{errorMessage(types.error)}</FormAlert>;

  const r = rules.data;
  // Sentences below carry their own currency word (LKR / රු. / ரூ.), so they take a plain number
  const plain = (amount: number) => format.number(amount, { maximumFractionDigits: 2 });
  const typeName = (type: MembershipType) =>
    locale === "si" ? type.nameSi : locale === "ta" ? type.nameTa : type.nameEn;
  const ages = (type: MembershipType) =>
    type.minAge != null && type.maxAge != null
      ? `${type.minAge}–${type.maxAge}`
      : type.maxAge != null
        ? `< ${type.maxAge + 1}`
        : `${type.minAge ?? 0}+`;

  return (
    <div className="grid max-w-3xl gap-10">
      <section aria-labelledby="types" className="grid gap-3">
        <h2 id="types" className="text-xl font-semibold">
          {t("membership")}
        </h2>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[32rem] text-sm">
            <thead>
              <tr className="border-b text-left">
                <th className="py-2 pr-3 font-medium">{t("type")}</th>
                <th className="py-2 pr-3 font-medium">{t("age")}</th>
                <th className="py-2 pr-3 text-right font-medium">{t("books")}</th>
                <th className="py-2 pr-3 text-right font-medium">{t("days")}</th>
                <th className="py-2 pr-3 text-right font-medium">{t("renewals")}</th>
                <th className="py-2 pr-3 text-right font-medium">{t("holds")}</th>
                <th className="py-2 text-right font-medium">{t("fee")}</th>
              </tr>
            </thead>
            <tbody>
              {types.data.map((type) => (
                <tr key={type.code} className="border-b last:border-0">
                  <th scope="row" className="py-2 pr-3 text-left font-medium">
                    {typeName(type)}
                  </th>
                  <td className="py-2 pr-3">{ages(type)}</td>
                  <td className="py-2 pr-3 text-right tabular-nums">{type.maxItems}</td>
                  <td className="py-2 pr-3 text-right tabular-nums">{type.loanDays}</td>
                  <td className="py-2 pr-3 text-right tabular-nums">{type.maxRenewals}</td>
                  <td className="py-2 pr-3 text-right tabular-nums">{type.maxHolds}</td>
                  <td className="py-2 text-right tabular-nums">
                    {Number(type.annualFee) === 0 ? t("free") : money(type.annualFee)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="text-sm text-muted-foreground">
          {t("validity", { years: r.membershipValidityYears })}
        </p>
      </section>

      <section aria-labelledby="loans" className="grid gap-2">
        <h2 id="loans" className="text-xl font-semibold">
          {t("loans")}
        </h2>
        <p>{t("loansText")}</p>
        <p>{t("renewText")}</p>
        <p>{t("referenceText")}</p>
      </section>

      <section aria-labelledby="fines" className="grid gap-2">
        <h2 id="fines" className="text-xl font-semibold">
          {t("fines")}
        </h2>
        <ul className="grid list-disc gap-1.5 pl-5">
          <li>
            {t("fineLate", {
              perDay: plain(Number(r.finePerDay)),
              grace: r.fineGraceDays,
              max: plain(Number(r.fineMaxPerItem)),
            })}
          </li>
          <li>
            {t("fineLost", {
              defaultPrice: plain(Number(r.lostDefaultPrice)),
              processing: plain(Number(r.lostProcessingFee)),
            })}
          </li>
          <li>{t("fineDamaged")}</li>
          <li className="font-medium">
            {t("fineBlock", { amount: plain(Number(r.blockBorrowingAt)) })}
          </li>
        </ul>
      </section>

      <section aria-labelledby="holds" className="grid gap-2">
        <h2 id="holds" className="text-xl font-semibold">
          {t("holdsTitle")}
        </h2>
        <p>{t("holdsText", { days: r.holdPickupOpenDays })}</p>
      </section>

      <section aria-labelledby="privacy" className="grid gap-2">
        <h2 id="privacy" className="text-xl font-semibold">
          {t("privacy")}
        </h2>
        <p>{t("privacyText", { days: r.documentRetentionDays })}</p>
      </section>

      <section aria-labelledby="faq" className="grid gap-3">
        <h2 id="faq" className="text-xl font-semibold">
          {t("faq")}
        </h2>
        {(["1", "2", "3"] as const).map((n) => (
          <details key={n} className="group rounded-lg border p-3">
            <summary className="cursor-pointer font-medium">{t(`faqItems.q${n}`)}</summary>
            <p className="mt-2 text-muted-foreground">{t(`faqItems.a${n}`)}</p>
          </details>
        ))}
      </section>
    </div>
  );
}
