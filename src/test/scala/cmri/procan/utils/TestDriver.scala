/**
MIT License

Copyright (c) 2017-2019 Children's Medical Research Institute (CMRI)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
  */
package cmri.procan.utils

import org.neo4j.driver.v1._

import cmri.procan.checkgraph.utils.WrappedNeo4jDriver

// Todo: uri and auth should come from config
object TestDriver {
  private val token: AuthToken = AuthTokens.basic("neo4j", "test")

  val driver: Driver = GraphDatabase.driver(
    "bolt://127.0.0.1:7687",
    token,
    Config.build.toConfig
  )

  val wrappedDriver = new WrappedNeo4jDriver(driver)
}
