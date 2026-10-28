"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";
import { Bell, Mail, MessageSquare, Send } from "lucide-react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { api, type Schema } from "@/lib/api";

type TemplateType = Schema<"TemplateTypeSummary">;
type Preview = Schema<"TemplatePreviewResponse">;
const LANGUAGES = ["en", "si", "ta"] as const;

/**
 * Message template previews (checklist step 324): pick a type and a language, see the email as
 * members get it (in a sandboxed frame: no scripts, no access to this page), its plain-text
 * version, the SMS and the in-app message. Made-up sample data; nothing is sent, unless the admin
 * sends a test email to themselves (step 325) to check it in a real mail app (Mailpit in dev).
 */
export function TemplatePreview() {
  const t = useTranslations("AdminTemplates");
  const tCommon = useTranslations("Common");
  const errorMessage = useAuthErrorMessage();
  const [type, setType] = useState<TemplateType["type"]>("DUE_SOON");
  const [lang, setLang] = useState<(typeof LANGUAGES)[number]>("en");

  const types = useQuery({
    queryKey: ["admin", "templates"],
    queryFn: () => api.get<TemplateType[]>("/admin/notifications/templates"),
    staleTime: Infinity,
  });
  const preview = useQuery({
    queryKey: ["admin", "templates", type, lang],
    queryFn: () =>
      api.get<Preview>(`/admin/notifications/templates/${type}/preview`, { query: { lang } }),
    placeholderData: keepPreviousData,
  });

  const p = preview.data;
  const sendTest = useMutation({
    mutationFn: () =>
      api.post<{ sentTo: string }>(`/admin/notifications/templates/${type}/test`, undefined, {
        query: { lang },
      }),
    onSuccess: ({ sentTo }) => toast.success(t("testSent", { email: sentTo })),
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-end gap-4">
        <label className="grid gap-1 text-sm">
          <span className="font-medium">{t("type")}</span>
          <select
            className="h-9 rounded-lg border bg-background px-2"
            value={type}
            onChange={(event) => setType(event.target.value as TemplateType["type"])}
          >
            {(types.data ?? []).map((option) => (
              <option key={option.type} value={option.type}>
                {option.type}
                {option.optional ? "" : ` (${t("alwaysSent")})`}
              </option>
            ))}
          </select>
        </label>
        <Tabs value={lang} onValueChange={(next) => setLang(next as typeof lang)}>
          <TabsList aria-label={t("language")}>
            {LANGUAGES.map((code) => (
              <TabsTrigger key={code} value={code}>
                {t(`languages.${code}`)}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
      </div>

      {preview.isPending ? (
        <div className="flex justify-center py-8">
          <HexLoader label={tCommon("loading")} />
        </div>
      ) : preview.isError ? (
        <FormAlert>{errorMessage(preview.error)}</FormAlert>
      ) : (
        p && (
          <div className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
            <section aria-labelledby="preview-email" className="grid content-start gap-2">
              <h2 id="preview-email" className="flex items-center gap-2 font-medium">
                <Mail aria-hidden className="size-4" />
                {t("email")}
              </h2>
              {p.emailHtml ? (
                <>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm">
                      <span className="text-muted-foreground">{t("subject")}: </span>
                      <strong>{p.emailSubject}</strong>
                    </p>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={sendTest.isPending}
                      onClick={() => sendTest.mutate()}
                    >
                      <Send aria-hidden />
                      {t("sendTest")}
                    </Button>
                  </div>
                  <Tabs defaultValue="html">
                    <TabsList>
                      <TabsTrigger value="html">{t("html")}</TabsTrigger>
                      <TabsTrigger value="text">{t("text")}</TabsTrigger>
                    </TabsList>
                    <TabsContent value="html">
                      {/* sandbox="": no scripts, no forms, can't reach this page or its login */}
                      <iframe
                        title={t("frameTitle")}
                        sandbox=""
                        srcDoc={p.emailHtml}
                        className="h-[640px] w-full rounded-lg border bg-white"
                      />
                    </TabsContent>
                    <TabsContent value="text">
                      <pre className="max-h-[640px] overflow-auto rounded-lg border bg-muted/40 p-3 text-sm whitespace-pre-wrap">
                        {p.emailText}
                      </pre>
                    </TabsContent>
                  </Tabs>
                </>
              ) : (
                <p className="text-sm text-muted-foreground">{t("noEmail")}</p>
              )}
            </section>

            <div className="grid content-start gap-6">
              <section aria-labelledby="preview-sms" className="grid gap-2">
                <h2 id="preview-sms" className="flex items-center gap-2 font-medium">
                  <MessageSquare aria-hidden className="size-4" />
                  {t("sms")}
                </h2>
                {p.sms ? (
                  <>
                    <p className="rounded-2xl rounded-bl-sm bg-muted p-3 text-sm">{p.sms}</p>
                    <p className="text-xs text-muted-foreground">
                      {t("smsLength", { count: p.sms.length })}
                    </p>
                  </>
                ) : (
                  <p className="text-sm text-muted-foreground">{t("noSms")}</p>
                )}
              </section>

              <section aria-labelledby="preview-inapp" className="grid gap-2">
                <h2 id="preview-inapp" className="flex items-center gap-2 font-medium">
                  <Bell aria-hidden className="size-4" />
                  {t("inApp")}
                </h2>
                {p.inAppTitle ? (
                  <div className="rounded-xl border p-3">
                    <p className="font-semibold">{p.inAppTitle}</p>
                    <p className="text-sm text-muted-foreground">{p.inAppBody}</p>
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">{t("noInApp")}</p>
                )}
              </section>

              <p className="text-xs text-muted-foreground">
                <Badge variant="outline">{t("sample")}</Badge> {t("sampleNote")}
              </p>
            </div>
          </div>
        )
      )}
    </div>
  );
}
