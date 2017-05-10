/*
 * This file is part of dependency-check-maven.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2014 Jeremy Long. All Rights Reserved.
 */

import java.nio.charset.Charset;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
 
// Analyse number of "Checking for updates"
String log = FileUtils.readFileToString(new File(basedir, "build.log"), Charset.defaultCharset().name());
int count = StringUtils.countMatches(log, "Checking for updates");
if (count > 1){
    System.out.println(String.format("The update should be unique, it is %s", count));
    return false;
}
return true;
