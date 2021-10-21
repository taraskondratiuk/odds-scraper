case class Event(discipline: String,
                 tournament: String, 
                 competitor1: String, 
                 competitor2: String,
                 bets: Seq[Bet],
                 scores: Seq[Score])

case class Bet(name: String, outcomes: Seq[Outcome])

case class Outcome(name: String, coef: Float)

case class Score(map: String, competitor1Score: String, competitor2Score: String)