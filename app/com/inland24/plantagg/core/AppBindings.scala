/*
 * Copyright (c) 2017 joesan @ http://github.com/joesan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.inland24.plantagg.core

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.inland24.plantagg.config.AppConfig
import monix.execution.Scheduler
import play.api.libs.concurrent.Execution.Implicits

// ******* Note: Both these imports should be here! Do not remove them!
import monix.cats._
import monix.eval.Task
// *******

import scala.language.higherKinds


trait AppBindings {

  def actorSystem: ActorSystem
  def materializer: Materializer

  def dbService: PowerPlantService[Task]
  def appConfig: AppConfig
  def supervisorActor: ActorRef

  def globalChannel: PowerPlantEventObservable
}
object AppBindings {

  def apply(system: ActorSystem, actorMaterializer: Materializer): AppBindings = new AppBindings {
    override val actorSystem: ActorSystem = system
    override val materializer: Materializer = actorMaterializer

    override val appConfig: AppConfig = AppConfig.load()

    // TODO: pass a separate thread pool / execution context in [Avoid using the default for db related operations]
    // Note: The type parameter should be explicitly specified, otherwise it won't compile!
    override val dbService: PowerPlantService[Task] = new PowerPlantService(
      new PowerPlantRepoAsTask(appConfig.dbConfig)(scala.concurrent.ExecutionContext.Implicits.global)
    )

    implicit val scheduler: Scheduler = Scheduler(Implicits.defaultContext)
    val globalChannel = PowerPlantEventObservable(scheduler)

    override val supervisorActor: ActorRef =
      system.actorOf(
        SupervisorActor.props(appConfig, globalChannel)(monix.execution.Scheduler.Implicits.global),
        s"${appConfig.appName}-supervisor"
      )
  }
}