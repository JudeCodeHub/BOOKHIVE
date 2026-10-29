"use client";

import type { ReactNode } from "react";
import { useFormatter, useTranslations } from "next-intl";
import {
  Bar,
  BarChart,
  CartesianGrid,
  LabelList,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

/**
 * Chart pieces for the reports (checklist step 373), following the dataviz method: thin marks,
 * one axis, recessive grid, text in text colours (never the series colour), a legend for 2+
 * series, and the numbers always reachable in a table. Series colours are the site's chart tokens,
 * checked with the palette validator in light and dark (globals.css), used in fixed order:
 * amber, blue, green.
 */
export const SERIES_COLORS = ["var(--chart-1)", "var(--chart-4)", "var(--chart-3)"] as const;

const AXIS = { fontSize: 12, fill: "var(--muted-foreground)" };

/** One line: which field of each row, and its name in the legend and tooltip. */
export type Series = { key: string; label: string };

/** Report rows (plain records from the API). */
type Row = Record<string, unknown>;

/** Lines over time, one tooltip listing every series at the hovered point. */
export function TimeChart({
  data,
  xKey,
  xLabel,
  series,
  value,
  summary,
  height = 280,
}: {
  data: Row[];
  xKey: string;
  /** How an x value is shown ("Oct 2026", "29 Oct") */
  xLabel: (x: string) => string;
  series: Series[];
  value: (n: number) => string;
  /** Read out by screen readers in place of the picture */
  summary: string;
  height?: number;
}) {
  return (
    <figure role="img" aria-label={summary} className="w-full">
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={data} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
          <CartesianGrid stroke="var(--border)" strokeDasharray="0" vertical={false} />
          <XAxis
            dataKey={xKey}
            tickFormatter={(x: string) => xLabel(x)}
            tick={AXIS}
            tickLine={false}
            axisLine={{ stroke: "var(--border)" }}
            minTickGap={24}
          />
          <YAxis
            tick={AXIS}
            tickLine={false}
            axisLine={false}
            width={56}
            tickFormatter={(n: number) => value(n)}
            allowDecimals={false}
          />
          <Tooltip
            cursor={{ stroke: "var(--muted-foreground)", strokeWidth: 1 }}
            content={({ active, payload, label }) =>
              active && payload?.length ? (
                <ChartTooltip title={xLabel(String(label))}>
                  {payload.map((p, i) => (
                    <TooltipRow
                      key={String(p.dataKey)}
                      color={SERIES_COLORS[i]}
                      label={series[i]?.label ?? String(p.dataKey)}
                      value={value(Number(p.value))}
                    />
                  ))}
                </ChartTooltip>
              ) : null
            }
          />
          {series.length > 1 && (
            <Legend
              iconType="plainline"
              wrapperStyle={{ fontSize: 12, color: "var(--foreground)" }}
            />
          )}
          {series.map((s, i) => (
            <Line
              key={s.key}
              dataKey={s.key}
              name={s.label}
              stroke={SERIES_COLORS[i]}
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--card)" }}
              isAnimationActive={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </figure>
  );
}

/** Columns over time for one count (e.g. new members per month). */
export function ColumnChart({
  data,
  xKey,
  xLabel,
  yKey,
  label,
  value,
  summary,
  height = 220,
}: {
  data: Row[];
  xKey: string;
  xLabel: (x: string) => string;
  yKey: string;
  label: string;
  value: (n: number) => string;
  summary: string;
  height?: number;
}) {
  return (
    <figure role="img" aria-label={summary} className="w-full">
      <ResponsiveContainer width="100%" height={height}>
        <BarChart data={data} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
          <CartesianGrid stroke="var(--border)" vertical={false} />
          <XAxis
            dataKey={xKey}
            tickFormatter={(x: string) => xLabel(x)}
            tick={AXIS}
            tickLine={false}
            axisLine={{ stroke: "var(--border)" }}
            minTickGap={16}
          />
          <YAxis tick={AXIS} tickLine={false} axisLine={false} width={40} allowDecimals={false} />
          <Tooltip
            cursor={{ fill: "var(--muted)", opacity: 0.6 }}
            content={({ active, payload, label: x }) =>
              active && payload?.length ? (
                <ChartTooltip title={xLabel(String(x))}>
                  <TooltipRow
                    color={SERIES_COLORS[1]}
                    label={label}
                    value={value(Number(payload[0].value))}
                  />
                </ChartTooltip>
              ) : null
            }
          />
          <Bar
            dataKey={yKey}
            name={label}
            fill={SERIES_COLORS[1]}
            radius={[4, 4, 0, 0]}
            maxBarSize={32}
            isAnimationActive={false}
          />
        </BarChart>
      </ResponsiveContainer>
    </figure>
  );
}

/** Horizontal bars, largest first, each with its value written at its end. */
export function BarList({
  rows,
  label,
  summary,
}: {
  rows: { name: string; value: number }[];
  label: string;
  summary: string;
}) {
  const format = useFormatter();
  const height = Math.max(120, rows.length * 32 + 16);
  return (
    <figure role="img" aria-label={summary} className="w-full">
      <ResponsiveContainer width="100%" height={height}>
        <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 48, bottom: 0, left: 0 }}>
          <XAxis type="number" hide allowDecimals={false} />
          <YAxis
            type="category"
            dataKey="name"
            width={180}
            tick={{ ...AXIS, fill: "var(--foreground)" }}
            tickLine={false}
            axisLine={false}
          />
          <Tooltip
            cursor={{ fill: "var(--muted)", opacity: 0.6 }}
            content={({ active, payload }) =>
              active && payload?.length ? (
                <ChartTooltip title={String(payload[0].payload.name)}>
                  <TooltipRow
                    color={SERIES_COLORS[1]}
                    label={label}
                    value={format.number(Number(payload[0].value))}
                  />
                </ChartTooltip>
              ) : null
            }
          />
          <Bar
            dataKey="value"
            name={label}
            fill={SERIES_COLORS[1]}
            radius={[0, 4, 4, 0]}
            maxBarSize={20}
            isAnimationActive={false}
          >
            <LabelList
              dataKey="value"
              position="right"
              style={{ fontSize: 12, fill: "var(--foreground)" }}
            />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </figure>
  );
}

function ChartTooltip({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 rounded-md border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
      <p className="text-muted-foreground">{title}</p>
      {children}
    </div>
  );
}

/** Value first (what the reader came for), then a short line key and the series name. */
function TooltipRow({ color, label, value }: { color: string; label: string; value: string }) {
  return (
    <p className="flex items-center gap-2">
      <span className="font-semibold tabular-nums">{value}</span>
      <span aria-hidden className="h-0.5 w-3 rounded" style={{ background: color }} />
      <span className="text-muted-foreground">{label}</span>
    </p>
  );
}

/** A headline number: label, value, optional note. */
export function StatTile({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="grid gap-1 rounded-lg border bg-card p-4">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="font-heading text-2xl font-semibold tabular-nums">{value}</p>
      {note && <p className="text-xs text-muted-foreground">{note}</p>}
    </div>
  );
}

/** The chart's numbers as a table, folded away until asked for. */
export function NumbersTable({
  headers,
  rows,
  numeric,
}: {
  headers: string[];
  rows: (string | number)[][];
  /** Which columns are numbers (right-aligned) */
  numeric: boolean[];
}) {
  const t = useTranslations("Reports");
  return (
    <details className="group rounded-md border">
      <summary className="cursor-pointer px-3 py-2 text-sm text-muted-foreground hover:text-foreground">
        {t("showNumbers")}
      </summary>
      <div className="max-h-96 overflow-auto border-t">
        <DataTable headers={headers} rows={rows} numeric={numeric} />
      </div>
    </details>
  );
}

export function DataTable({
  headers,
  rows,
  numeric,
}: {
  headers: string[];
  rows: (string | number)[][];
  numeric: boolean[];
}) {
  return (
    <table className="w-full text-sm">
      <thead className="sticky top-0 bg-muted">
        <tr>
          {headers.map((h, i) => (
            <th
              key={h}
              scope="col"
              className={`px-3 py-2 font-medium ${numeric[i] ? "text-right" : "text-left"}`}
            >
              {h}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, r) => (
          <tr key={r} className="border-t">
            {row.map((cell, i) => (
              <td key={i} className={`px-3 py-1.5 ${numeric[i] ? "text-right tabular-nums" : ""}`}>
                {cell}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
