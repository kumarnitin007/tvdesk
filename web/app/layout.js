import "./globals.css";

export const metadata = {
  title: "TV Desk",
  description: "Edit the board that shows on the TV",
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
