/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.builder.shrinker;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.shrinker.Shrinker.ShrinkType;
import com.android.utils.FileUtils;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

/**
 * Simple {@link ShrinkerGraph} implementation that uses strings, maps and Java serialization.
 */
public class JavaSerializationShrinkerGraph implements ShrinkerGraph<String> {

    private final File mStateDir;

    private static final CacheLoader<String, Counter> CACHE_LOADER =
            new CacheLoader<String, Counter>() {
                @Override
                public Counter load(@NonNull String unused) throws Exception {
                    return new Counter();
                }
            };

    private ConcurrentMap<String, ClassInfo> mClasses = Maps.newConcurrentMap();

    private SetMultimap<String, String> mMembers =
            Multimaps.synchronizedSetMultimap(HashMultimap.<String, String>create());

    private EnumMap<ShrinkType, LoadingCache<String, Counter>> mReferenceCounters;

    private SetMultimap<String, Dependency<String>> mDependencies =
            Multimaps.synchronizedSetMultimap(HashMultimap.<String, Dependency<String>>create());

    public JavaSerializationShrinkerGraph(File stateDir) {
        mStateDir = checkNotNull(stateDir);
        mReferenceCounters =
                new EnumMap<ShrinkType, LoadingCache<String, Counter>>(ShrinkType.class);
        for (ShrinkType shrinkType : ShrinkType.values()) {
            LoadingCache<String, Counter> counters = CacheBuilder.newBuilder()
                    .concurrencyLevel(Runtime.getRuntime().availableProcessors())
                    .build(CACHE_LOADER);

            mReferenceCounters.put(shrinkType, counters);
        }
    }

    @Override
    public String addMember(String owner, String name, String desc) {
        String fullName = getFullMethodName(owner, name, desc);
        mMembers.put(owner, fullName);
        return fullName;
    }

    @Override
    public String getMemberReference(String className, String memberName, String methodDesc) {
        return getFullMethodName(className, memberName, methodDesc);
    }

    @NonNull
    private static String getFullMethodName(String className, String methodName, String typeDesc) {
        return className + "." + methodName + ":" + typeDesc;
    }

    @Override
    public void addDependency(String source, String target, DependencyType type) {
        Dependency<String> dep = new Dependency<String>(target, type);
        mDependencies.put(source, dep);
    }

    @Override
    public Set<Dependency<String>> getDependencies(String member) {
        return Sets.newHashSet(mDependencies.get(member));
    }

    @Override
    public Set<String> getMembers(String klass) {
        return Sets.newHashSet(mMembers.get(klass));
    }

    @Override
    public boolean incrementAndCheck(String member, DependencyType type, ShrinkType shrinkType) {
        try {
            return mReferenceCounters.get(shrinkType).get(member).incrementAndCheck(type);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveState() throws IOException {
        ObjectOutputStream stream =
                new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(getStateFile())));
        try {
            stream.writeObject(mClasses);
            stream.writeObject(mMembers);
            stream.writeObject(mDependencies);
            Map<ShrinkType, Map<String, Counter>> countersMap = Maps.newHashMap();
            for (Map.Entry<ShrinkType, LoadingCache<String, Counter>> entry
                    : mReferenceCounters.entrySet()) {
                countersMap.put(entry.getKey(), Maps.newHashMap(entry.getValue().asMap()));
            }
            stream.writeObject(countersMap);
        } finally {
            stream.close();
        }
    }

    @Override
    public boolean isReachable(String member, ShrinkType shrinkType) {
        try {
            return mReferenceCounters.get(shrinkType).get(member).isReachable();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeDependency(String source, Dependency<String> dep) {
        mDependencies.remove(source, dep);
    }

    @Override
    public boolean decrementAndCheck(String member, DependencyType type, ShrinkType shrinkType) {
        try {
            return mReferenceCounters.get(shrinkType).get(member).decrementAndCheck(type);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getSuperclass(String klass) {
        return mClasses.get(klass).superclass;
    }

    @Nullable
    @Override
    public String findMatchingMethod(String klass, String method) {
        String methodToLookFor = klass + "." + getMemberId(method);
        if (mMembers.containsEntry(klass, methodToLookFor)) {
            return methodToLookFor;
        } else {
            return null;
        }
    }

    @Override
    public boolean isLibraryMember(String method) {
        return mClasses.get(getClassForMember(method)).isLibraryClass();
    }

    @Override
    public String[] getInterfaces(String klass) {
        return mClasses.get(klass).interfaces;
    }

    @Override
    public void checkDependencies() {
        for (Dependency<String> dep : mDependencies.values()) {
            String target = dep.target;
            if (!target.contains(".")) {
                if (!mClasses.containsKey(target)) {
                    throw new IllegalStateException("Invalid dependency target: " + target);
                }
            } else {
                if (!mMembers.containsEntry(getClassForMember(target), target)) {
                    throw new IllegalStateException("Invalid dependency target: " + target);
                }
            }
        }
    }

    @Override
    public boolean keepClass(String klass, ShrinkType shrinkType) {
        try {
            LoadingCache<String, Counter> counters = mReferenceCounters.get(shrinkType);
            Counter counter = counters.get(klass);
            return counter.isReachable();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private File getStateFile() {
        return new File(mStateDir, "shrinker.bin");
    }

    @SuppressWarnings("unchecked") // readObject() returns an Object, we need to cast it.
    @Override
    public void loadState() throws IOException {
        ObjectInputStream stream =
                new ObjectInputStream(new BufferedInputStream(new FileInputStream(getStateFile())));

        try {
            mClasses = (ConcurrentMap<String, ClassInfo>) stream.readObject();
            mMembers = (SetMultimap<String, String>) stream.readObject();
            mDependencies = (SetMultimap<String, Dependency<String>>) stream.readObject();
            Map<ShrinkType, Map<String, Counter>> countersMap =
                    (Map<ShrinkType, Map<String, Counter>>) stream.readObject();

            for (Map.Entry<ShrinkType, Map<String, Counter>> entry : countersMap.entrySet()) {
                LoadingCache<String, Counter> cache = mReferenceCounters.get(entry.getKey());
                cache.invalidateAll();
                cache.putAll(entry.getValue());
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            stream.close();
        }
    }

    @Override
    public void removeStoredState() throws IOException {
        FileUtils.emptyFolder(mStateDir);
    }

    @Override
    public Collection<String> getClassesToKeep(ShrinkType shrinkType) {
        try {
            List<String> classesToKeep = Lists.newArrayList();
            for (Map.Entry<String, ClassInfo> entry : mClasses.entrySet()) {
                if (entry.getValue().isLibraryClass()) {
                    // Skip lib
                    continue;
                }
                LoadingCache<String, Counter> counters = mReferenceCounters.get(shrinkType);
                Counter counter = counters.get(entry.getKey());
                if (counter.isReachable()) {
                    classesToKeep.add(entry.getKey());
                }
            }

            return classesToKeep;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public File getClassFile(String klass) {
        return mClasses.get(klass).classFile;
    }

    @Override
    public Set<String> getMembersToKeep(String klass, ShrinkType shrinkType) {
        try {
            Set<String> memberIds = Sets.newHashSet();
            for (String member : mMembers.get(klass)) {
                if (mReferenceCounters.get(shrinkType).get(member).isReachable()) {
                    String memberId = getMemberId(member);
                    memberIds.add(memberId);
                }
            }

            return memberIds;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private static String getMemberId(String member) {
        return member.substring(member.indexOf('.') + 1);
    }

    @Override
    public String getClassForMember(String member) {
        return member.substring(0, member.indexOf('.'));
    }

    @Override
    public String getClassReference(String className) {
        return className;
    }

    @Override
    public String addClass(String name, String superName, String[] interfaces, File classFile) {
        //noinspection unchecked - ASM API
        ClassInfo classInfo = new ClassInfo(classFile, superName, interfaces);
        mClasses.put(name, classInfo);
        return name;
    }

    private static final class ClassInfo implements Serializable {
        final File classFile;
        final String superclass;
        final String[] interfaces;

        private ClassInfo(File classFile, String superclass, String[] interfaces) {
            this.classFile = classFile;
            this.superclass = superclass;
            this.interfaces = interfaces;
        }

        boolean isLibraryClass() {
            return classFile == null;
        }
    }

    private static final class Counter implements Serializable {
        int required = 0;
        int isOverridden = 0;
        int neededForInheritance = 0;

        synchronized boolean decrementAndCheck(DependencyType type) {
            boolean before = isReachable();
            switch (type) {
                case REQUIRED:
                    required--;
                    break;
                case IS_OVERRIDDEN:
                    isOverridden--;
                    break;
                case NEEDED_FOR_INHERITANCE:
                    neededForInheritance--;
                    break;
            }
            boolean after = isReachable();
            return before != after;
        }

        synchronized boolean incrementAndCheck(DependencyType type) {
            boolean before = isReachable();
            switch (type) {
                case REQUIRED:
                    required++;
                    break;
                case IS_OVERRIDDEN:
                    isOverridden++;
                    break;
                case NEEDED_FOR_INHERITANCE:
                    neededForInheritance++;
                    break;
            }
            boolean after = isReachable();
            return before != after;
        }

        synchronized boolean isReachable() {
            return required > 0 || (isOverridden > 0 && neededForInheritance > 0);
        }
    }
}