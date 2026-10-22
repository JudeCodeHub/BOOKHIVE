import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { FormField } from "@/components/forms/form-field";
import { Input } from "@/components/ui/input";
import { renderWithIntl } from "@/test/render";

describe("FormField", () => {
  it("links the label to the input so screen readers announce it", () => {
    renderWithIntl(<FormField label="Email">{(field) => <Input {...field} />}</FormField>);

    const input = screen.getByLabelText("Email");
    expect(input).toBeInTheDocument();
    expect(input).not.toHaveAttribute("aria-invalid");
  });

  it("marks required fields for screen readers", () => {
    renderWithIntl(
      <FormField label="Email" required>
        {(field) => <Input {...field} />}
      </FormField>,
    );

    expect(screen.getByText("(required)")).toHaveClass("sr-only");
  });

  it("describes the input with its hint and error, and marks it invalid", () => {
    renderWithIntl(
      <FormField label="Password" hint="At least 8 characters" error="Too short">
        {(field) => <Input type="password" {...field} />}
      </FormField>,
    );

    const input = screen.getByLabelText("Password");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAccessibleDescription("At least 8 characters Too short");
  });
});
