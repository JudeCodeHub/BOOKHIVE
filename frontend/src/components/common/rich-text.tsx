import { cn } from "@/lib/utils";

/**
 * Text written in the staff editor (event descriptions, news): HTML already cleaned by the API
 * (RichText.clean: paragraphs, headings, lists, links; no scripts or styles), so it can be shown
 * as it is. Styled here because the site has no typography plugin.
 */
export function RichText({ html, className }: { html: string; className?: string }) {
  return (
    <div
      className={cn(
        "grid max-w-prose gap-3 leading-relaxed",
        "[&_h2]:mt-2 [&_h2]:font-heading [&_h2]:text-xl [&_h2]:font-semibold",
        "[&_h3]:mt-1 [&_h3]:font-heading [&_h3]:text-lg [&_h3]:font-semibold",
        "[&_li]:mt-1 [&_ol]:list-decimal [&_ol]:pl-6 [&_ul]:list-disc [&_ul]:pl-6",
        "[&_blockquote]:border-l-4 [&_blockquote]:pl-4 [&_blockquote]:text-muted-foreground [&_blockquote]:italic",
        "[&_a]:font-medium [&_a]:text-primary [&_a]:underline [&_a]:underline-offset-4",
        className,
      )}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
