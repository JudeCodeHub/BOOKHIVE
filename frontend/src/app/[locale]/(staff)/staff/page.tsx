import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { StaffDashboard } from "@/features/dashboard/staff-dashboard";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffHome");
  return { title: t("title") };
}

export default function StaffDashboardPage() {
  return <StaffDashboard />;
}
