package models

object Models {
  case class EventCoefs(
    sportName: String,
    discipline: String,
    tournament: String,
    competitor1: String,
    competitor2: String,
    scores: Seq[Score],
    bets: Seq[Bet],
  )

  case class Bet(
    name: String,
    outcomes: Seq[Outcome],
  )

  case class Outcome(
    name: String,
    coef: Float,
  )

  case class Score(
    map: String,
    competitor1Score: String,
    competitor2Score: String,
  )
}
