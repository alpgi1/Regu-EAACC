import { useEffect } from "react";
import Nav from "@/components/landing/Nav";
import Hero from "@/components/landing/Hero";
import UrgencyBar from "@/components/landing/UrgencyBar";
import Stakes from "@/components/landing/Stakes";
import Methodology from "@/components/landing/Methodology";
import HowItWorks from "@/components/landing/HowItWorks";
import UseCases from "@/components/landing/UseCases";
import Comparison from "@/components/landing/Comparison";
import Security from "@/components/landing/Security";
import FAQ from "@/components/landing/FAQ";
import FinalCTA from "@/components/landing/FinalCTA";
import Contact from "@/components/landing/Contact";
import Footer from "@/components/landing/Footer";
import { initLenis, destroyLenis } from "@/lib/lenis";

export default function Landing() {
  useEffect(() => {
    initLenis();
    return () => destroyLenis();
  }, []);

  return (
    <>
      <Nav />
      <main id="main-content" className="bg-[var(--bg-base)]">
        <Hero />
        <Stakes />
        <Methodology />
        <HowItWorks />
        <UseCases />
        <Comparison />
        <Security />
        <FAQ />
        <FinalCTA />
        <Contact />
      </main>
      <Footer />
      <UrgencyBar />
    </>
  );
}
