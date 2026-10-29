"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { MoreHorizontal, UserPlus } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormField } from "@/components/forms/form-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { FormAlert } from "@/features/auth/auth-card";
import { useErrorMessage, useFieldErrors } from "@/features/auth/errors";
import { useSession } from "@/features/auth/use-session";
import { api, type Schema } from "@/lib/api";

type Staff = Schema<"StaffSummary">;
type StaffRole = "LIBRARIAN" | "ADMIN";

const QUERY_KEY = ["admin", "staff"] as const;

/** What an admin is about to do to someone; confirmed in a dialog first. */
type Action =
  | { kind: "role"; person: Staff; role: StaffRole }
  | { kind: "deactivate"; person: Staff }
  | { kind: "reactivate"; person: Staff };

/**
 * Staff accounts (ADM-4, checklist step 371): invite librarians and admins, change their role,
 * switch accounts off and on, resend an invite. Admins can't change their own account here (a
 * second admin has to), and the last active admin can't be removed: the API refuses both.
 */
export function StaffAccounts() {
  const t = useTranslations("AdminStaff");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorText = useErrorMessage("AdminStaff");
  const queryClient = useQueryClient();
  const me = useSession().user?.id;
  const [inviting, setInviting] = useState(false);
  const [action, setAction] = useState<Action | null>(null);

  const query = useQuery({
    queryKey: QUERY_KEY,
    queryFn: () => api.get<Staff[]>("/admin/staff"),
  });

  const run = useMutation({
    mutationFn: (a: Action) =>
      a.kind === "role"
        ? api.put<Staff>(`/admin/staff/${a.person.id}/role`, { role: a.role })
        : api.post<Staff>(`/admin/staff/${a.person.id}/${a.kind}`),
    onSuccess: (_, a) => {
      setAction(null);
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY });
      toast.success(
        a.kind === "role"
          ? t("done.role", { name: a.person.fullName, role: t(`roles.${a.role}`) })
          : t(`done.${a.kind}`, { name: a.person.fullName }),
      );
    },
    onError: (error) => {
      setAction(null);
      toast.error(errorText(error));
    },
  });
  const resend = useMutation({
    mutationFn: (person: Staff) => api.post<Staff>(`/admin/staff/${person.id}/resend-invite`),
    onSuccess: (person) => toast.success(t("done.resent", { email: person.email })),
    onError: (error) => toast.error(errorText(error)),
  });

  if (query.isPending) return <HexLoader label={tCommon("loading")} />;
  if (query.isError) return <FormAlert>{errorText(query.error)}</FormAlert>;

  const activeAdmins = query.data.filter((p) => p.role === "ADMIN" && p.status === "ACTIVE").length;

  return (
    <div className="grid gap-4">
      <div>
        <Button onClick={() => setInviting(true)}>
          <UserPlus aria-hidden />
          {t("invite")}
        </Button>
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("person")}</TableHead>
            <TableHead>{t("role")}</TableHead>
            <TableHead>{t("status")}</TableHead>
            <TableHead>{t("since")}</TableHead>
            <TableHead>
              <span className="sr-only">{t("actions")}</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {query.data.map((p) => {
            const isMe = p.id === me;
            const otherRole: StaffRole = p.role === "ADMIN" ? "LIBRARIAN" : "ADMIN";
            const lastAdmin = p.role === "ADMIN" && p.status === "ACTIVE" && activeAdmins <= 1;
            return (
              <TableRow key={p.id} className={p.status === "DISABLED" ? "opacity-60" : undefined}>
                <TableCell>
                  <span className="font-medium">{p.fullName}</span>
                  {isMe && (
                    <Badge variant="secondary" className="ml-2">
                      {t("you")}
                    </Badge>
                  )}
                  <span className="block text-xs text-muted-foreground">{p.email}</span>
                </TableCell>
                <TableCell>
                  <Badge variant={p.role === "ADMIN" ? "default" : "outline"}>
                    {t(`roles.${p.role === "ADMIN" ? "ADMIN" : "LIBRARIAN"}`)}
                  </Badge>
                </TableCell>
                <TableCell>
                  <Badge
                    variant={
                      p.status === "ACTIVE"
                        ? "available"
                        : p.status === "DISABLED"
                          ? "destructive"
                          : "on-loan"
                    }
                  >
                    {t(`statuses.${p.status}`)}
                  </Badge>
                </TableCell>
                <TableCell className="text-sm whitespace-nowrap">
                  {p.joinedAt
                    ? t("joined", {
                        date: format.dateTime(new Date(p.joinedAt), { dateStyle: "medium" }),
                      })
                    : t("invited", {
                        date: format.dateTime(new Date(p.invitedAt), { dateStyle: "medium" }),
                      })}
                </TableCell>
                <TableCell className="text-right">
                  {isMe ? (
                    <span className="text-xs text-muted-foreground">{t("notYourself")}</span>
                  ) : (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={t("actionsFor", { name: p.fullName })}
                        >
                          <MoreHorizontal aria-hidden />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {p.status !== "DISABLED" && (
                          <DropdownMenuItem
                            disabled={lastAdmin}
                            onSelect={() => setAction({ kind: "role", person: p, role: otherRole })}
                          >
                            {t(`makeRole.${otherRole}`)}
                          </DropdownMenuItem>
                        )}
                        {p.status === "PENDING_VERIFICATION" && (
                          <DropdownMenuItem onSelect={() => resend.mutate(p)}>
                            {t("resendInvite")}
                          </DropdownMenuItem>
                        )}
                        <DropdownMenuSeparator />
                        {p.status === "DISABLED" ? (
                          <DropdownMenuItem
                            onSelect={() => setAction({ kind: "reactivate", person: p })}
                          >
                            {t("reactivate")}
                          </DropdownMenuItem>
                        ) : (
                          <DropdownMenuItem
                            disabled={lastAdmin}
                            className="text-destructive"
                            onSelect={() => setAction({ kind: "deactivate", person: p })}
                          >
                            {t("deactivate")}
                          </DropdownMenuItem>
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  )}
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>

      <Dialog open={action !== null} onOpenChange={(open) => !open && setAction(null)}>
        <DialogContent>
          {action && (
            <>
              <DialogHeader>
                <DialogTitle>
                  {action.kind === "role"
                    ? t("confirm.roleTitle", {
                        name: action.person.fullName,
                        role: t(`roles.${action.role}`),
                      })
                    : t(`confirm.${action.kind}Title`, { name: action.person.fullName })}
                </DialogTitle>
                <DialogDescription>
                  {action.kind === "role"
                    ? t(`confirm.role${action.role}`)
                    : t(`confirm.${action.kind}Text`)}
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="outline" onClick={() => setAction(null)}>
                  {t("cancel")}
                </Button>
                <Button
                  variant={action.kind === "deactivate" ? "destructive" : "default"}
                  disabled={run.isPending}
                  onClick={() => run.mutate(action)}
                >
                  {action.kind === "role" ? t(`makeRole.${action.role}`) : t(action.kind)}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={inviting} onOpenChange={setInviting}>
        <DialogContent>
          {inviting && <InviteForm onDone={() => setInviting(false)} />}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function InviteForm({ onDone }: { onDone: () => void }) {
  const t = useTranslations("AdminStaff");
  const v = useTranslations("Auth.validation");
  const errorText = useErrorMessage("AdminStaff");
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);

  const schema = z.object({
    fullName: z.string().trim().min(1, v("required")).max(150, v("tooLong")),
    email: z.string().trim().min(1, v("required")).email(t("emailInvalid")).max(254, v("tooLong")),
    phone: z
      .string()
      .trim()
      .refine((s) => s === "" || /^(\+94|0)[0-9]{9}$/.test(s), t("phoneInvalid")),
    role: z.enum(["LIBRARIAN", "ADMIN"]),
    preferredLanguage: z.enum(["en", "si", "ta"]),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      fullName: "",
      email: "",
      phone: "",
      role: "LIBRARIAN",
      preferredLanguage: "en",
    },
  });
  const { errors, isSubmitting } = form.formState;
  const selectClass = "h-8 rounded-lg border bg-transparent px-2.5 text-sm";

  const applyFieldErrors = useFieldErrors();
  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    try {
      const person = await api.post<Staff>("/admin/staff", {
        ...values,
        phone: values.phone || undefined,
      });
      toast.success(t("done.invited", { email: person.email }));
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY });
      onDone();
    } catch (error) {
      if (!applyFieldErrors(error, form.setError)) setFormError(errorText(error));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      <DialogHeader>
        <DialogTitle>{t("inviteTitle")}</DialogTitle>
        <DialogDescription>{t("inviteText")}</DialogDescription>
      </DialogHeader>
      {formError && <FormAlert>{formError}</FormAlert>}
      <FormField label={t("fullName")} error={errors.fullName?.message} required>
        {(field) => <Input autoComplete="off" {...field} {...form.register("fullName")} />}
      </FormField>
      <FormField label={t("email")} error={errors.email?.message} required>
        {(field) => (
          <Input type="email" autoComplete="off" {...field} {...form.register("email")} />
        )}
      </FormField>
      <FormField label={t("phone")} hint={t("phoneHint")} error={errors.phone?.message}>
        {(field) => <Input type="tel" {...field} {...form.register("phone")} />}
      </FormField>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label={t("role")} hint={t("roleHint")} error={errors.role?.message}>
          {(field) => (
            <select className={selectClass} {...field} {...form.register("role")}>
              <option value="LIBRARIAN">{t("roles.LIBRARIAN")}</option>
              <option value="ADMIN">{t("roles.ADMIN")}</option>
            </select>
          )}
        </FormField>
        <FormField label={t("language")} hint={t("languageHint")}>
          {(field) => (
            <select className={selectClass} {...field} {...form.register("preferredLanguage")}>
              <option value="en">English</option>
              <option value="si">සිංහල</option>
              <option value="ta">தமிழ்</option>
            </select>
          )}
        </FormField>
      </div>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>
          {t("cancel")}
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? t("sending") : t("sendInvite")}
        </Button>
      </DialogFooter>
    </form>
  );
}
