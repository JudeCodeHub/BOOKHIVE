import type { ReactElement } from "react";
import { render } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import messages from "../../messages/en.json";

/**
 * render() with the English translations available, for components that use useTranslations.
 * Tests assert on the English text.
 */
export function renderWithIntl(ui: ReactElement) {
  return render(
    <NextIntlClientProvider locale="en" messages={messages} timeZone="Asia/Colombo">
      {ui}
    </NextIntlClientProvider>,
  );
}
