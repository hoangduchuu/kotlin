/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.deprecatedByOverriddenMessage
import org.jetbrains.kotlin.resolve.getDeprecation

class OverridingDeprecatedMemberInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                registerProblemIfNeeded(declaration, declaration.nameIdentifier ?: return)
            }

            override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
                registerProblemIfNeeded(accessor, accessor.namePlaceholder)
            }

            private fun registerProblemIfNeeded(declaration: KtDeclaration, targetForProblem: PsiElement) {
                if (declaration is KtDestructuringDeclarationEntry) return
                val accessorDescriptor = declaration.resolveToDescriptor() as? CallableMemberDescriptor ?: return

                val message = accessorDescriptor.getDeprecation()?.deprecatedByOverriddenMessage() ?: return
                val problem = holder.manager.createProblemDescriptor(
                        targetForProblem,
                        message,
                        /* showTooltip = */ true,
                        ProblemHighlightType.LIKE_DEPRECATED,
                        isOnTheFly
                )
                holder.registerProblem(problem)
            }
        }
    }
}