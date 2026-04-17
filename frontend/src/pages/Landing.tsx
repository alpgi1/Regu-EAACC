/**
 * Landing page — composes all sections in order.
 * The analysis app lives at /app; this page lives at /.
 */

import NavBar from "@/components/sections/NavBar";
import Hero from "@/components/sections/Hero";
import Stakes from "@/components/sections/Stakes";
import Methodology from "@/components/sections/Methodology";
import SecondaryCta from "@/components/sections/SecondaryCta";
import Contact from "@/components/sections/Contact";
import About from "@/components/sections/About";
import Footer from "@/components/sections/Footer";

export default function Landing() {
  return (
    <>
      <NavBar />
      <main id="main-content">
        <Hero />
        <Stakes />
        <Methodology />
        <SecondaryCta />
        <Contact />
        <About />
      </main>
      <Footer />
    </>
  );
}
