import Desk from "./desk";
import FamilyTaskApp from "../components/FamilyTaskApp";

export default function Page({ searchParams }) {
  return searchParams?.legacy === "1" ? <Desk /> : <FamilyTaskApp />;
}
