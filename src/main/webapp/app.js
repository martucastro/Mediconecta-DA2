// Single-select groups: pressing a button un-presses its siblings.
document.querySelectorAll("[data-single-select]").forEach((group) => {
  group.addEventListener("click", (event) => {
    const btn = event.target.closest("button");
    if (!btn || btn.disabled) return;
    group.querySelectorAll("button").forEach((b) => b.setAttribute("aria-pressed", String(b === btn)));
    group.dispatchEvent(new CustomEvent("select", { detail: btn }));
  });
});

// Availability: the CTA mirrors the selected slot.
const slots = document.querySelector(".slots");
const reserve = document.getElementById("reserve");
slots?.addEventListener("select", (e) => {
  reserve.textContent = `Reservar ${e.detail.textContent} hs`;
});

// Hold: 5-minute countdown. ponytail: client-side only, the real hold lives in ServicioDeTurnos.
const clock = document.getElementById("countdown");
if (clock) {
  const TOTAL = 300;
  const bar = document.getElementById("progress");
  const end = Date.now() + Number(clock.dataset.seconds) * 1000;
  const tick = () => {
    const left = Math.max(0, Math.round((end - Date.now()) / 1000));
    clock.textContent = `${String(Math.floor(left / 60)).padStart(2, "0")}:${String(left % 60).padStart(2, "0")}`;
    bar.style.width = `${(left / TOTAL) * 100}%`;
    if (left === 0) location.href = "disponibilidad.html";
    else setTimeout(tick, 250);
  };
  tick();
}
