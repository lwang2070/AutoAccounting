/*
 * Copyright (C) 2026 ankio(ankio@ankio.net)
 * Licensed under the Apache License, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-3.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.ankio.auto.update

import net.ankio.auto.BuildConfig
import net.ankio.auto.utils.CoroutineUtils.withIO
import net.ankio.auto.utils.PrefManager

/**
 * 应用更新工具类：负责检查新版本并给出下载地址。
 *
 * 版本信息来源是自己的 GitHub Release，不再依赖作者的服务器。
 * 对话框由 [net.ankio.auto.ui.activity.HomeActivity] 根据返回结果展示。
 */
object AppUpdateHelper {

    /**
     * 是否允许自动检查更新（遵循用户设置）。
     */
    fun isAutoCheckEnabled(): Boolean = PrefManager.autoCheckAppUpdate

    /**
     * APK 下载地址，指向自己仓库的 GitHub Release。
     */
    fun buildApkUrl(version: String): String = GithubReleaseAPI.apkUrl(version)

    /**
     * 检查是否有新版本。
     *
     * 返回 [Result] 让调用方分得清两种情况：
     * - `success(null)`：查询成功，但没有比当前更新的版本
     * - `success(update)`：有新版本
     * - `failure`：网络不通、被限流等，检查本身失败了
     */
    suspend fun check(): Result<UpdateModel?> {
        val result = withIO { GithubReleaseAPI.latest(PrefManager.appChannel) }
        if (result.isFailure) return result

        val update = result.getOrNull() ?: return Result.success(null)
        val newer = VersionUtils.isCloudVersionNewer(BuildConfig.VERSION_NAME, update.version)
        return Result.success(if (newer) update else null)
    }
}
