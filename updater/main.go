package main

import (
	"archive/zip"
	"flag"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

func main() {
	var path = flag.String("path", "", "program run path")
	var file = flag.String("file", "", "zip file path")
	flag.Parse()

    fmt.Println("LumenTV Updater v1.0.4")

	if *path == "" {
		fmt.Println("path is required")
		return
	}
	if *file == "" {
		fmt.Println("file is required")
		return
	}

	// 处理路径：如果指向 app 子目录，则使用其父目录作为目标
	targetPath := *path
	pathBase := filepath.Base(strings.TrimRight(*path, string(filepath.Separator)))
	if strings.ToLower(pathBase) == "app" {
		// 如果路径以 app 结尾，使用父目录作为目标
		targetPath = filepath.Dir(*path)
		fmt.Printf("Detected app directory, using parent as target: %s\n", targetPath)
	}

	// 确保路径以正确的分隔符结尾
	if !strings.HasSuffix(targetPath, string(filepath.Separator)) {
		targetPath = targetPath + string(filepath.Separator)
	}

	_, err := os.Stat(targetPath)
	if os.IsNotExist(err) {
		fmt.Printf("target path is not exist %s\n", targetPath)
		return
	}
	_, err = os.Stat(*file)
	if os.IsNotExist(err) {
		fmt.Printf("zip file is not exist %s\n", *file)
		return
	}

	executable, err := os.Executable()
	if err != nil {
		fmt.Printf("get executable path err:%v\n", err)
		return
	}

	fmt.Printf("Starting update...\n")
	fmt.Printf("Original path: %s\n", *path)
	fmt.Printf("Target path: %s\n", targetPath)
	fmt.Printf("Zip file: %s\n", *file)
	fmt.Printf("Updater path: %s\n", executable)

	backupPath := targetPath + ".backup"
	fmt.Printf("Backup path: %s\n", backupPath)

	if err := backupDirectory(targetPath, backupPath); err != nil {
		fmt.Printf("Backup failed: %v\n", err)
		return
	}

	fmt.Println("Backup completed")

	if err := removeAllFiles(targetPath, executable); err != nil {
		fmt.Printf("Remove files failed: %v\n", err)
		restoreBackup(backupPath, targetPath)
		return
	}

	fmt.Println("Old files removed")

	if err := extractZip(*file, targetPath); err != nil {
		fmt.Printf("Extract failed: %v\n", err)
		restoreBackup(backupPath, targetPath)
		return
	}

	fmt.Println("Extract completed")

	if err := os.RemoveAll(backupPath); err != nil {
		fmt.Printf("Remove backup failed: %v\n", err)
	}

	fmt.Println("Backup removed")

	if err := startProgram(targetPath); err != nil {
		fmt.Printf("Start program failed: %v\n", err)
		return
	}

	fmt.Println("Update completed successfully")
}

func backupDirectory(src, dst string) error {
	return filepath.Walk(src, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		relPath, err := filepath.Rel(src, path)
		if err != nil {
			return err
		}

		dstPath := filepath.Join(dst, relPath)

		if info.IsDir() {
			return os.MkdirAll(dstPath, info.Mode())
		}

		return copyFile(path, dstPath)
	})
}

func copyFile(src, dst string) error {
	srcFile, err := os.Open(src)
	if err != nil {
		return err
	}
	defer srcFile.Close()

	dstFile, err := os.OpenFile(dst, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0644)
	if err != nil {
		return err
	}
	defer dstFile.Close()

	_, err = io.Copy(dstFile, srcFile)
	return err
}

func removeAllFiles(dir, excludePath string) error {
    maxRetries := 5
    retryDelay := time.Millisecond * 500

    for attempt := 0; attempt < maxRetries; attempt++ {
        if attempt > 0 {
            fmt.Printf("Retry attempt %d/%d...\n", attempt+1, maxRetries)
            time.Sleep(retryDelay)
        }

        err := filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
            if err != nil {
                if os.IsNotExist(err) {
                    return nil
                }
                return err
            }

            if path == dir {
                return nil
            }

            if strings.EqualFold(path, excludePath) {
                return nil
            }

            if info.IsDir() {
                // 先尝试删除目录中的文件
                return filepath.Walk(path, func(subPath string, subInfo os.FileInfo, subErr error) error {
                    if subErr != nil {
                        return nil // 忽略子目录错误
                    }
                    if !subInfo.IsDir() {
                        if _, statErr := os.Stat(subPath); !os.IsNotExist(statErr) {
                            os.Remove(subPath) // 尝试删除但不返回错误
                        }
                    }
                    return nil
                })
            }

            if _, err := os.Stat(path); os.IsNotExist(err) {
                return nil
            }

            return os.Remove(path)
        })

        if err == nil {
            return nil
        }

        if attempt == maxRetries-1 {
            return err
        }
    }

    return fmt.Errorf("failed to remove files after %d attempts", maxRetries)
}


func extractZip(zipPath, dest string) error {
    r, err := zip.OpenReader(zipPath)
    if err != nil {
        return err
    }
    defer r.Close()

    fmt.Printf("Extracting %s to %s\n", zipPath, dest)

    // 检测是否有统一的根目录
    rootFolder := detectRootFolder(r)
    if rootFolder != "" {
        fmt.Printf("Will strip root folder: %s\n", rootFolder)
    }

    for _, f := range r.File {
        var fpath string
        if rootFolder != "" && strings.HasPrefix(f.Name, rootFolder+"/") {
            // 如果有统一的根目录，去掉这一层
            relativePath := f.Name[len(rootFolder)+1:]
            fpath = filepath.Join(dest, relativePath)
            fmt.Printf("Stripping root: %s -> %s\n", f.Name, relativePath)
        } else {
            fpath = filepath.Join(dest, f.Name)
            if rootFolder == "" {
                fmt.Printf("Direct extract: %s\n", f.Name)
            }
        }

        // 跳过空路径和根目录本身
        if fpath == dest || fpath == dest+"/" || filepath.Base(fpath) == rootFolder {
            continue
        }

        if f.FileInfo().IsDir() {
            os.MkdirAll(fpath, f.Mode())
            continue
        }

        if err := os.MkdirAll(filepath.Dir(fpath), 0755); err != nil {
            return err
        }

        outFile, err := os.OpenFile(fpath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, f.Mode())
        if err != nil {
            return err
        }

        rc, err := f.Open()
        if err != nil {
            outFile.Close()
            return err
        }

        _, err = io.Copy(outFile, rc)
        rc.Close()
        outFile.Close()

        if err != nil {
            return err
        }
    }

    fmt.Println("Extraction completed")
    return nil
}

// 检测ZIP文件是否有一个统一的根目录
func detectRootFolder(r *zip.ReadCloser) string {
    folderCount := make(map[string]int)
    fileCount := 0
    rootFolders := make([]string, 0)

    // 统计所有文件和文件夹
    for _, f := range r.File {
        // 跳过目录本身
        if f.FileInfo().IsDir() && !strings.HasSuffix(f.Name, "/") {
            continue
        }

        parts := strings.Split(strings.Trim(f.Name, "/"), "/")
        if len(parts) > 0 && parts[0] != "" {
            folderName := parts[0]
            if _, exists := folderCount[folderName]; !exists {
                rootFolders = append(rootFolders, folderName)
            }
            folderCount[folderName]++
            fileCount++
        }
    }

    fmt.Printf("Analysis: fileCount=%d, root folders: %v\n", fileCount, rootFolders)
    for folder, count := range folderCount {
        fmt.Printf("  Folder '%s': %d files\n", folder, count)
    }

    // 如果只有一个根目录，且名称与目标应用名匹配，则去除这个根目录
    if len(rootFolders) == 1 {
        rootFolder := rootFolders[0]
        // 检查是否是应用名称相关的根目录
        if strings.Contains(strings.ToLower(rootFolder), "lumentv") ||
           strings.Contains(strings.ToLower(rootFolder), "app") {
            fmt.Printf("Detected application root folder '%s', will strip it\n", rootFolder)
            return rootFolder
        }
    }

    fmt.Printf("Keeping original structure\n")
    return ""
}


func restoreBackup(backupPath, destPath string) error {
	fmt.Println("Restoring backup...")
	if err := os.RemoveAll(destPath); err != nil {
		return err
	}
	return os.Rename(backupPath, destPath)
}

func startProgram(path string) error {
	var exeName string
	if runtime.GOOS == "windows" {
		exeName = "LumenTV.exe"
	} else {
		exeName = "LumenTV"
	}

	exePath := filepath.Join(path, exeName)

	cmd := &exec.Cmd{}
	if runtime.GOOS == "windows" {
		cmd.Path = exePath
		cmd.Args = []string{exePath}
	} else {
		cmd.Path = exePath
		cmd.Args = []string{exePath}
	}

	cmd.Dir = path

	return cmd.Start()
}
