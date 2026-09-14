package work.lclpnet.ap2.api

import work.lclpnet.kibu.scheduler.api.TaskScheduler

/**
 * Provides a [TaskScheduler] that can be used as an implicit context argument.
 * Implementors get access to the scheduler extension functions in all of their members without additional scoping.
 */
interface SchedulerHolder {

    val scheduler: TaskScheduler
}
