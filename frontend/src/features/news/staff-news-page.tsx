"use client";

import { BackLink } from "@/components/layout/back-link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { EyeOff, ExternalLink, Send, Trash2 } from "lucide-react";
import { useParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { PictureUpload } from "@/components/forms/picture-upload";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Link, useRouter } from "@/i18n/navigation";
import { api } from "@/lib/api";
import { NewsStatusBadge, STAFF_NEWS_KEY, type StaffNewsPost } from "./staff-news";
import { StaffNewsForm } from "./staff-news-form";

/**
 * One news post for staff (checklist step 347): edit, picture, publish / take off the website,
 * delete. The website caches news for a few minutes, so a change shows there shortly after.
 */
export function StaffNewsPage() {
  const t = useTranslations("StaffNews");
  const tCommon = useTranslations("Common");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const [deleting, setDeleting] = useState(false);
  const detailKey = [...STAFF_NEWS_KEY, "detail", id];

  const query = useQuery({
    queryKey: detailKey,
    queryFn: () => api.get<StaffNewsPost>(`/staff/news/${id}`),
  });

  const saved = (post: StaffNewsPost) => {
    queryClient.setQueryData(detailKey, post);
    void queryClient.invalidateQueries({ queryKey: STAFF_NEWS_KEY });
  };
  const publish = useMutation({
    mutationFn: (on: boolean) =>
      api.post<StaffNewsPost>(`/staff/news/${id}/${on ? "publish" : "unpublish"}`),
    onSuccess: (post, on) => {
      saved(post);
      toast.success(on ? t("published") : t("unpublished"));
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const remove = useMutation({
    mutationFn: () => api.delete(`/staff/news/${id}`),
    onSuccess: () => {
      toast.success(t("deleted"));
      queryClient.removeQueries({ queryKey: detailKey });
      void queryClient.invalidateQueries({ queryKey: STAFF_NEWS_KEY });
      router.push("/staff/news");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (query.isPending) return <HexLoader label={tCommon("loading")} />;
  if (query.isError) return <FormAlert>{errorMessage(query.error)}</FormAlert>;

  const post = query.data;
  const isPublished = post.status === "PUBLISHED";
  return (
    <div className="grid gap-4">
      <BackLink href="/staff/news">{t("back")}</BackLink>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-xl font-semibold">{post.title}</h1>
        <NewsStatusBadge status={post.status} />
      </div>
      <div className="flex flex-wrap gap-2">
        {isPublished ? (
          <>
            <Button size="sm" variant="outline" asChild>
              <Link href={`/news/${post.id}`} target="_blank">
                <ExternalLink aria-hidden />
                {t("viewOnSite")}
              </Link>
            </Button>
            <Button
              size="sm"
              variant="secondary"
              disabled={publish.isPending}
              onClick={() => publish.mutate(false)}
            >
              <EyeOff aria-hidden />
              {t("unpublish")}
            </Button>
          </>
        ) : (
          <Button size="sm" disabled={publish.isPending} onClick={() => publish.mutate(true)}>
            <Send aria-hidden />
            {t("publish")}
          </Button>
        )}
        <Button size="sm" variant="ghost" onClick={() => setDeleting(true)}>
          <Trash2 aria-hidden />
          {t("delete")}
        </Button>
      </div>
      {isPublished && <p className="text-sm text-muted-foreground">{t("cacheNote")}</p>}

      <Tabs defaultValue="details" className="gap-4">
        <TabsList>
          <TabsTrigger value="details">{t("tabDetails")}</TabsTrigger>
          <TabsTrigger value="picture">{t("tabPicture")}</TabsTrigger>
        </TabsList>
        <TabsContent value="details">
          <StaffNewsForm key={post.id} post={post} />
        </TabsContent>
        <TabsContent value="picture">
          <PictureUpload<StaffNewsPost>
            path={`/staff/news/${post.id}/image`}
            hasImage={post.imageVersion != null}
            version={post.imageVersion}
            hint={t("pictureHint")}
            onChange={saved}
          />
        </TabsContent>
      </Tabs>

      <Dialog open={deleting} onOpenChange={setDeleting}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("deleteTitle")}</DialogTitle>
            <DialogDescription>
              {isPublished ? t("deleteTextPublished") : t("deleteText")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleting(false)}>
              {t("keep")}
            </Button>
            <Button
              variant="destructive"
              disabled={remove.isPending}
              onClick={() => remove.mutate()}
            >
              {t("delete")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
