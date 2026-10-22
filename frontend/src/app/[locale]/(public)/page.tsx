import { getTranslations } from "next-intl/server";

export default async function Home() {
  const t = await getTranslations("Home");

  return (
    <section className="flex flex-1 flex-col items-center justify-center gap-3 px-4 py-24 text-center">
      <h1 className="text-4xl font-semibold">BookHive</h1>
      <p className="text-lg text-muted-foreground">{t("tagline")}</p>
    </section>
  );
}
