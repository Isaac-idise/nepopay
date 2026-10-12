import { Link } from 'react-router-dom'
import { Button, Card } from '../components/ui'
import { copy } from '../lib/personality'

const steps = [
  { n: '1', title: 'Create an account', text: 'Takes under a minute. No real documents, no real bank.' },
  { n: '2', title: 'Get ₦100,000 play money', text: 'Credited instantly, recorded like a real transaction in the ledger.' },
  { n: '3', title: 'Send it to a friend', text: 'Search their name or @username, confirm with your PIN, done.' },
]

const features = [
  ['🔒', 'PIN-protected transfers', 'Wrong PIN three times and transfers lock for 15 minutes.'],
  ['📒', 'A real double-entry ledger', 'Every move is a balanced debit and credit you can inspect.'],
  ['🧐', 'Fraud checks that explain themselves', 'Risky transfers are held for review instead of vanishing.'],
  ['🔁', 'Safe to press Send twice', 'Duplicate requests can never move money twice.'],
]

export default function Landing() {
  return (
    <div>
      <section className="py-10 text-center sm:py-16">
        <p className="mx-auto mb-4 inline-block rounded-full bg-plantain px-3 py-1 text-xs font-extrabold uppercase tracking-wide">
          Simulated Nigerian fintech sandbox
        </p>
        <h1 className="text-5xl font-black leading-[1.05] tracking-tight sm:text-7xl">
          Banking, but <span className="text-jollof">make am.</span>
        </h1>
        <p className="mx-auto mt-5 max-w-xl text-lg text-ink/75">
          Open an account, receive ₦100,000 of play money, and send it to anybody who also signs up.
          {' '}<strong>{copy.demoLine}</strong>
        </p>
        <div className="mt-7 flex flex-wrap justify-center gap-3">
          <Link to="/register"><Button className="!px-6 !py-3 text-base">Create my account</Button></Link>
          <Link to="/login"><Button variant="ghost" className="!px-6 !py-3 text-base">I already have one</Button></Link>
        </div>
      </section>

      <section aria-labelledby="how" className="grid gap-3 sm:grid-cols-3">
        <h2 id="how" className="sr-only">How it works</h2>
        {steps.map((s) => (
          <Card key={s.n}>
            <div className="mb-2 grid size-8 place-items-center rounded-full bg-ink text-sm font-extrabold text-cream">{s.n}</div>
            <h3 className="font-bold">{s.title}</h3>
            <p className="mt-1 text-sm text-ink/70">{s.text}</p>
          </Card>
        ))}
      </section>

      <section className="mt-8 rounded-3xl bg-ink p-6 text-cream sm:p-8" aria-labelledby="demo">
        <h2 id="demo" className="text-xl font-extrabold">What “DEMO MODE” means</h2>
        <p className="mt-2 max-w-2xl text-sm text-cream/80">
          Nothing here is connected to a real bank, card or payment network. Every naira is simulated and lives only
          in this app’s database. Please do not enter a real password or PIN you use anywhere else.
        </p>
      </section>

      <section className="mt-8 grid gap-3 sm:grid-cols-2" aria-label="Features">
        {features.map(([icon, title, text]) => (
          <Card key={title} className="flex gap-3">
            <div className="text-2xl" aria-hidden="true">{icon}</div>
            <div>
              <h3 className="font-bold">{title}</h3>
              <p className="text-sm text-ink/70">{text}</p>
            </div>
          </Card>
        ))}
      </section>
    </div>
  )
}
