/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.psi.stubs.impl

import com.google.common.collect.Lists
import com.intellij.psi.PsiClass
import com.intellij.psi.impl.java.stubs.PsiClassStub
import com.intellij.psi.stubs.PsiClassHolderFileStub
import com.intellij.psi.stubs.PsiFileStubImpl
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.util.io.StringRef
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.JetFile
import org.jetbrains.kotlin.psi.stubs.KotlinFileStub
import org.jetbrains.kotlin.psi.stubs.elements.JetStubElementTypes

public class KotlinFileStubImpl(jetFile: JetFile?, private val packageName: StringRef, private val isScript: Boolean)// SCRIPT: PsiJetFileStubImpl knows about scripting
    : PsiFileStubImpl<JetFile>(jetFile), KotlinFileStub, PsiClassHolderFileStub<JetFile> {

    public constructor(jetFile: JetFile?, packageName: String, isScript: Boolean)
        : this(jetFile, StringRef.fromString(packageName)!!, isScript) {
    }

    override fun getPackageFqName(): FqName {
        return FqName(StringRef.toString(packageName)!!)
    }

    override fun isScript(): Boolean {
        return isScript
    }

    override fun getType(): IStubFileElementType<KotlinFileStub> {
        return JetStubElementTypes.FILE
    }

    override fun toString(): String {
        return "PsiJetFileStubImpl[" + "package=" + getPackageFqName().asString() + "]"
    }

    override fun getClasses(): Array<PsiClass> {
        val result = Lists.newArrayList<PsiClass>()
        for (child in getChildrenStubs()) {
            if (child is PsiClassStub<*>) {
                result.add(child.getPsi() as PsiClass)
            }
        }
        return result.toArray<PsiClass>(arrayOfNulls<PsiClass>(result.size()))
    }
}
