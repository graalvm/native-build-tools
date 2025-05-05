/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package layered.mn.app;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;

@Controller
public class HelloController {
    @Get("/")
    @Produces(MediaType.TEXT_PLAIN)
    public String sayHello() {
        return sayHello("layered images");
    }

    @Get("/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String sayHello(String name) {
        return "Hello, "+ name + "!";
    }

    @Get("/bye/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String sayBye(String name) {
        return "Bye, "+ name + "!";
    }

}
