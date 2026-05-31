package app.domain.services

/**
 * Constants for fact codes used in statistics extraction.
 */
object FactCodes {
    val COMMIT_DAY_WEEK = 1
    val COMMIT_DAY_TIME = 2
    val COMMIT_LINE_NUM_AVG = 8
    val COMMIT_NUM = 9
    val COMMIT_NUM_TO_LINE_NUM = 12
    val COMMIT_SHARE = 16
    val COMMIT_SHARE_REPO_AVG = 17
    val LINE_LONGEVITY = 3
    val LINE_LONGEVITY_REPO = 4
    val LINE_LEN_AVG = 10
    val LINE_NUM = 11
    val REPO_DATE_START = 5
    val REPO_DATE_END = 6
    val REPO_TEAM_SIZE = 7
    val VARIABLE_NAMING = 13
    val VARIABLE_NAMING_SNAKE_CASE = 0
    val VARIABLE_NAMING_CAMEL_CASE = 1
    val VARIABLE_NAMING_OTHER = 2
    val INDENTATION = 14
    val INDENTATION_TABS = 0
    val INDENTATION_SPACES = 1
    val COLLEAGUES = 15
}
