"use client";

import { useEffect, type ReactNode } from "react";
import { EditorContent, useEditor, useEditorState, type Editor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import {
  Bold,
  Heading2,
  Heading3,
  Italic,
  Link as LinkIcon,
  List,
  ListOrdered,
  Quote,
  Redo2,
  Undo2,
} from "lucide-react";
import { useTranslations } from "next-intl";
import type { FieldControlProps } from "@/components/forms/form-field";
import { cn } from "@/lib/utils";

/**
 * A small word-processor box (Tiptap) for event descriptions and news (checklist steps 344,
 * 347). It only offers what the API keeps (RichText.clean: paragraphs, H2/H3, bold, italic,
 * lists, quotes, links), so what staff see is what gets published. The value is HTML; an empty
 * box gives "".
 */
export function RichTextEditor({
  value,
  onChange,
  disabled,
  id,
  "aria-invalid": invalid,
  "aria-describedby": describedBy,
}: FieldControlProps & {
  value: string;
  onChange: (html: string) => void;
  disabled?: boolean;
}) {
  const t = useTranslations("RichTextEditor");
  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [2, 3] },
        code: false,
        codeBlock: false,
        horizontalRule: false,
        strike: false,
        link: {
          openOnClick: false,
          autolink: true,
          protocols: ["http", "https", "mailto"],
          defaultProtocol: "https",
        },
      }),
    ],
    content: value,
    editable: !disabled,
    // Rendered in the browser only: avoids a hydration mismatch with the server HTML
    immediatelyRender: false,
    editorProps: {
      attributes: {
        id,
        role: "textbox",
        "aria-multiline": "true",
        ...(invalid ? { "aria-invalid": "true" } : {}),
        ...(describedBy ? { "aria-describedby": describedBy } : {}),
        class: cn(
          "min-h-40 px-3 py-2 text-sm leading-relaxed outline-none",
          "[&_p]:my-2 [&_h2]:mt-3 [&_h2]:text-lg [&_h2]:font-semibold [&_h3]:mt-2 [&_h3]:font-semibold",
          "[&_ul]:list-disc [&_ol]:list-decimal [&_ul]:pl-6 [&_ol]:pl-6",
          "[&_blockquote]:border-l-4 [&_blockquote]:pl-3 [&_blockquote]:text-muted-foreground",
          "[&_a]:text-primary [&_a]:underline",
        ),
      },
    },
    onUpdate: ({ editor }) => onChange(editor.isEmpty ? "" : editor.getHTML()),
  });

  // A new value from outside (form reset, a different record loaded) replaces the content
  useEffect(() => {
    if (editor && value !== (editor.isEmpty ? "" : editor.getHTML())) {
      editor.commands.setContent(value, { emitUpdate: false });
    }
  }, [editor, value]);

  useEffect(() => {
    editor?.setEditable(!disabled);
  }, [editor, disabled]);

  return (
    <div
      className={cn(
        "rounded-lg border focus-within:ring-3 focus-within:ring-ring/50",
        invalid && "border-destructive",
        disabled && "opacity-60",
      )}
    >
      {editor && <Toolbar editor={editor} label={t("toolbar")} />}
      <EditorContent editor={editor} />
    </div>
  );
}

function Toolbar({ editor, label }: { editor: Editor; label: string }) {
  const t = useTranslations("RichTextEditor");
  // Re-render the buttons when the selection's formatting changes
  const state = useEditorState({
    editor,
    selector: ({ editor: e }) => ({
      bold: e.isActive("bold"),
      italic: e.isActive("italic"),
      h2: e.isActive("heading", { level: 2 }),
      h3: e.isActive("heading", { level: 3 }),
      bullet: e.isActive("bulletList"),
      ordered: e.isActive("orderedList"),
      quote: e.isActive("blockquote"),
      link: e.isActive("link"),
      canUndo: e.can().undo(),
      canRedo: e.can().redo(),
    }),
  });

  function link() {
    const current = editor.getAttributes("link").href as string | undefined;
    const href = window.prompt(t("linkPrompt"), current ?? "https://");
    if (href === null) return;
    if (href.trim() === "" || href.trim() === "https://") {
      editor.chain().focus().extendMarkRange("link").unsetLink().run();
    } else {
      editor.chain().focus().extendMarkRange("link").setLink({ href: href.trim() }).run();
    }
  }

  return (
    <div role="toolbar" aria-label={label} className="flex flex-wrap gap-0.5 border-b p-1">
      <Tool
        label={t("bold")}
        active={state.bold}
        onClick={() => editor.chain().focus().toggleBold().run()}
      >
        <Bold />
      </Tool>
      <Tool
        label={t("italic")}
        active={state.italic}
        onClick={() => editor.chain().focus().toggleItalic().run()}
      >
        <Italic />
      </Tool>
      <Tool
        label={t("heading")}
        active={state.h2}
        onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
      >
        <Heading2 />
      </Tool>
      <Tool
        label={t("subheading")}
        active={state.h3}
        onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
      >
        <Heading3 />
      </Tool>
      <Tool
        label={t("bulletList")}
        active={state.bullet}
        onClick={() => editor.chain().focus().toggleBulletList().run()}
      >
        <List />
      </Tool>
      <Tool
        label={t("numberedList")}
        active={state.ordered}
        onClick={() => editor.chain().focus().toggleOrderedList().run()}
      >
        <ListOrdered />
      </Tool>
      <Tool
        label={t("quote")}
        active={state.quote}
        onClick={() => editor.chain().focus().toggleBlockquote().run()}
      >
        <Quote />
      </Tool>
      <Tool label={t("link")} active={state.link} onClick={link}>
        <LinkIcon />
      </Tool>
      <span className="mx-1 w-px bg-border" aria-hidden />
      <Tool
        label={t("undo")}
        disabled={!state.canUndo}
        onClick={() => editor.chain().focus().undo().run()}
      >
        <Undo2 />
      </Tool>
      <Tool
        label={t("redo")}
        disabled={!state.canRedo}
        onClick={() => editor.chain().focus().redo().run()}
      >
        <Redo2 />
      </Tool>
    </div>
  );
}

function Tool({
  label,
  active,
  disabled,
  onClick,
  children,
}: {
  label: string;
  active?: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      aria-pressed={active ?? undefined}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground disabled:opacity-40 [&_svg]:size-4",
        active && "bg-primary/10 text-primary",
      )}
    >
      {children}
    </button>
  );
}
