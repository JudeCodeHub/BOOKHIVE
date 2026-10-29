"use client";

import { useFormatter } from "next-intl";

/**
 * Money, written the same way everywhere (checklist step 382). The reader's language decides the
 * sign: "LKR 195.00" in English and Tamil, "රු. 195.00" in Sinhala, which is how the sentences
 * around it already read. Always two decimals, so a column of amounts lines up.
 *
 *   const money = useMoney();
 *   <td>{money(fine.balance)}</td>
 *
 * Amounts inside a sentence are different: those messages carry their own currency word and take
 * a plain number (e.g. Rules.fineBlock, Membership.type.fee).
 */
export function useMoney() {
  const format = useFormatter();
  return (amount: number | string) =>
    format.number(Number(amount), {
      style: "currency",
      currency: "LKR",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
}
