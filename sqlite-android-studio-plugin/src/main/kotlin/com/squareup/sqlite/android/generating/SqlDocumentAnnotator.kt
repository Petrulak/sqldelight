package com.squareup.sqlite.android.generating

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.squareup.sqlite.android.SqliteCompiler
import com.squareup.sqlite.android.SqliteCompiler.Status
import com.squareup.sqlite.android.lang.SqliteFile

internal class SqlDocumentAnnotator : ExternalAnnotator<TableGenerator, SqlDocumentAnnotator.Generation>() {
  private val localFileSystem = LocalFileSystem.getInstance()
  private val sqliteCompiler = SqliteCompiler<ASTNode>()

  override fun collectInformation(file: PsiFile) = ApplicationManager.getApplication().runReadAction(Computable { TableGenerator.create(file) })

  override fun doAnnotate(tableGenerator: TableGenerator): Generation? {
    val status = sqliteCompiler.write(tableGenerator)
    val file = localFileSystem.findFileByIoFile(tableGenerator.outputDirectory)
    file?.refresh(false, true)
    val generatedFile = file?.findFileByRelativePath(
        tableGenerator.packageDirectory + "/" + tableGenerator.generatedFileName + ".java")
    return Generation(status, generatedFile)
  }

  override fun apply(file: PsiFile, generation: Generation, holder: AnnotationHolder) {
    when (generation.status.result) {
      SqliteCompiler.Status.Result.FAILURE -> holder.createErrorAnnotation(
          generation.status.originatingElement,
          generation.status.errorMessage)
      SqliteCompiler.Status.Result.SUCCESS -> {
        if (generation.generatedFile == null) return
        val document = FileDocumentManager.getInstance().getDocument(generation.generatedFile)
        document?.createGuardedBlock(0, document.textLength)
        (file as SqliteFile).generatedFile = file.getManager().findFile(generation.generatedFile)
      }
    }
  }

  class Generation internal constructor(val status: Status<ASTNode>, val generatedFile: VirtualFile?)
}
