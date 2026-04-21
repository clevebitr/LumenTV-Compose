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

    fmt.Println("LumenTV Updater v1.0.5")

	if *path == "" {
		fmt.Println("path is required")
		return
	}
	if *file == "" {
		fmt.Println("file is required")
		return
	}

	targetPath := *path
	pathBase := filepath.Base(strings.TrimRight(*path, string(filepath.Separator)))
	if strings.ToLower(pathBase) == "app" {
		targetPath = filepath.Dir(*path)
		fmt.Printf("Detected app directory, using parent as target: %s\n", targetPath)
	}

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

		var toDelete []string
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

			toDelete = append(toDelete, path)
			return nil
		})

		if err != nil {
			if attempt == maxRetries-1 {
				return err
			}
			continue
		}

		for i := len(toDelete) - 1; i >= 0; i-- {
			path := toDelete[i]

			if _, statErr := os.Stat(path); os.IsNotExist(statErr) {
				continue
			}
			if strings.EqualFold(path, excludePath) {
				continue
			}

			if err := os.RemoveAll(path); err != nil {
				fmt.Printf("Warning: failed to remove %s: %v\n", path, err)
				if attempt == maxRetries-1 {
					return fmt.Errorf("failed to remove %s: %w", path, err)
				}
			} else {
				fmt.Printf("Removed: %s\n", path)
			}
		}

		remainingCount := 0
		filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
			if err != nil || path == dir || strings.EqualFold(path, excludePath) {
				return nil
			}
			remainingCount++
			return nil
		})

		if remainingCount == 0 {
			fmt.Println("All old files removed successfully")
			return nil
		}

		if attempt == maxRetries-1 {
			return fmt.Errorf("failed to remove all files after %d attempts, %d items remaining", maxRetries, remainingCount)
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

    rootFolder := detectRootFolder(r)
    if rootFolder != "" {
        fmt.Printf("Will strip root folder: %s\n", rootFolder)
    }

    for _, f := range r.File {
        var fpath string
        if rootFolder != "" && strings.HasPrefix(f.Name, rootFolder+"/") {
            relativePath := f.Name[len(rootFolder)+1:]
            fpath = filepath.Join(dest, relativePath)
            fmt.Printf("Stripping root: %s -> %s\n", f.Name, relativePath)
        } else {
            fpath = filepath.Join(dest, f.Name)
            if rootFolder == "" {
                fmt.Printf("Direct extract: %s\n", f.Name)
            }
        }

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

func detectRootFolder(r *zip.ReadCloser) string {
    folderCount := make(map[string]int)
    fileCount := 0
    rootFolders := make([]string, 0)

    for _, f := range r.File {
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

    if len(rootFolders) == 1 {
        rootFolder := rootFolders[0]
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
