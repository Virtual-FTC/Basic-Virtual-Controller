/*
 * Copyright 2020 Google LLC
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

package org.firstinspires.ftc.robotcore.internal.opmode;

import com.qualcomm.robotcore.util.ClassUtil;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.firstinspires.ftc.robotcore.external.ExportToBlocks;

/**
 * Class filter used to provide blocks that can call java methods in teamcode.
 *
 * @author Liz Looney
 */
public class BlocksClassFilter implements ClassFilter {
  private static final Pattern methodLookupStringPattern = Pattern.compile(
      "([^ ]+) ([^\\\\(]+).*");

  private final Map<Class, Set<Method>> methodsByClass = new TreeMap<>(new Comparator<Class>() {
    @Override public int compare(Class c1, Class c2) {
      return c1.getName().compareTo(c2.getName());
    }
  });
  private final Map<String, Method> allMethods = new HashMap<>();

  // Singleton

  private static class InstanceHolder {
    public static BlocksClassFilter theInstance = new BlocksClassFilter();
  }

  public static BlocksClassFilter getInstance() {
    return InstanceHolder.theInstance;
  }

  private BlocksClassFilter() {
  }

  // ClassFilter

  @Override
  public void filterAllClassesStart() {
    methodsByClass.clear();
    allMethods.clear();
  }

  @Override
  public void filterOnBotJavaClassesStart() {
    Iterator<Map.Entry<Class, Set<Method>>> iter = methodsByClass.entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<Class, Set<Method>> entry = iter.next();
      if (OnBotJavaDeterminer.isOnBotJava(entry.getKey())) {
        for (Method method : entry.getValue()) {
          allMethods.remove(getLookupString(method));
        }
        iter.remove();
      }
    }
  }


  @Override
  public void filterClass(Class clazz) {
    exploreClass(clazz);
  }

  @Override
  public void filterOnBotJavaClass(Class clazz) {
    exploreClass(clazz);
  }

  @Override
  public void filterAllClassesComplete() {
  }

  @Override
  public void filterOnBotJavaClassesComplete() {
  }

  private void exploreClass(Class clazz) {
    if (getPackage(clazz).equals("org.firstinspires.ftc.teamcode")) {
      Set<Method> methods = new TreeSet<>(new Comparator<Method>() {
        @Override
        public int compare(Method m1, Method m2) {
          int result = m1.getName().compareToIgnoreCase(m2.getName());
          if (result != 0) {
            return result;
          }
          result = m1.getName().compareTo(m2.getName());
          if (result != 0) {
            return result;
          }
          Class[] p1 = m1.getParameterTypes();
          Class[] p2 = m2.getParameterTypes();
          for (int i = 0; i < p1.length && i < p2.length; i++) {
            result = p1[i].getSimpleName().compareToIgnoreCase(p2[i].getSimpleName());
            if (result != 0) {
              return result;
            }
            result = p1[i].getSimpleName().compareTo(p2[i].getSimpleName());
            if (result != 0) {
              return result;
            }
          }
          return p2.length - p1.length;
        }
      });
      for (Method method : ClassUtil.getLocalDeclaredMethods(clazz)) {
        int modifiers = method.getModifiers();
        int requiredModifiers = Modifier.PUBLIC | Modifier.STATIC;
        if ((modifiers & requiredModifiers) != requiredModifiers) {
          continue;
        }
        int prohibitedModifiers = Modifier.ABSTRACT;
        if ((method.getModifiers() & prohibitedModifiers) != 0) {
          continue;
        }
        if (!method.isAnnotationPresent(ExportToBlocks.class)) {
          continue;
        }
        // Note(lizlooney): We can't handle methods with over 21 parameters, which seems beyond
        // reasonable to me. See comment in
        // lib/Blocks/src/main/java/com/google/blocks/ftcrobotcontroller/runtime/MiscAccess.java
        // just before the callJava method.
        if (method.getParameterTypes().length > 21) {
          continue;
        }
        methods.add(method);
      }
      if (!methods.isEmpty()) {
        methodsByClass.put(clazz, methods);
        for (Method method : methods) {
          allMethods.put(getLookupString(method), method);
        }
      }
    }
  }

  private static String getPackage(Class clazz) {
    Package pkg = clazz.getPackage();
    if (pkg != null) {
      return pkg.getName();
    }
    // For OnBotJava classes, getPackage() returns null.
    while (clazz.getEnclosingClass() != null) {
      clazz = clazz.getEnclosingClass();
    }
    String name = clazz.getName();
    int lastDot = name.lastIndexOf('.');
    if (lastDot == -1) {
      return "";
    }
    return name.substring(0, lastDot);
  }

  // other public methods

  /**
   * Returns the methods, keyed by class. The caller should not modify the collection.
   */
  public Map<Class, Set<Method>> getMethodsByClass() {
    return methodsByClass;
  }

  /**
   * Returns the method associated with the given lookup string, or null if it is not found.
   */
  public Method findMethod(String lookupString) {
    return allMethods.get(lookupString);
  }

  /**
   * Returns a lookup string that can be saved and used later to retrieve the given method if it
   * still exists.
   */
  public static String getLookupString(Method method) {
    StringBuilder sb = new StringBuilder()
        .append(method.getDeclaringClass().getName())
        .append(" ")
        .append(method.getName())
        .append("(");
    String delimeter = "";
    for (Class p : method.getParameterTypes()) {
      sb.append(delimeter).append(p.getName());
      delimeter = ",";
    }
    sb.append(") ").append(method.getReturnType().getName());
    return sb.toString();
  }

  public static String getUserVisibleName(String methodLookupString) {
    Matcher matcher = methodLookupStringPattern.matcher(methodLookupString);
    if (matcher.find()) {
      String className = matcher.group(1);
      int lastDot = className.lastIndexOf(".");
      if (lastDot != -1) {
        className = className.substring(lastDot + 1);
      }
      className = className.replace('$', '.');
      return className + "." + matcher.group(2);
    }
    return "";
  }
}
