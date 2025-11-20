# How to Export YouTube Cookies for Backend

YouTube requires authentication to prevent bot detection. Follow these steps to export your cookies:

## Method 1: Using Browser Extension (Easiest)

1. **Install Cookie Editor Extension**:
   - Chrome: https://chrome.google.com/webstore/detail/cookie-editor/hlkenndednhfkekhgcdicdfddnkalmdm
   - Firefox: https://addons.mozilla.org/en-US/firefox/addon/cookie-editor/

2. **Export YouTube Cookies**:
   - Go to https://www.youtube.com (make sure you're logged in)
   - Click the Cookie Editor extension icon
   - Click "Export" button
   - Click "Netscape" format
   - Save the file as `cookies.txt`

3. **Place the cookies.txt file**:
   - Copy `cookies.txt` to the backend directory: `D:\JC\tv\JJUTV\backend\cookies.txt`

## Method 2: Using yt-dlp command (Alternative)

Open Command Prompt and run:
```bash
yt-dlp --cookies-from-browser chrome --cookies cookies.txt "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
```

This will create a `cookies.txt` file in the current directory.

## IMPORTANT Notes:

- **Never commit cookies.txt to Git** (it's already in .gitignore)
- Cookies expire - you may need to re-export them every few months
- Keep cookies.txt safe - it contains your YouTube session

## After exporting:

The backend will automatically detect and use the cookies.txt file for authentication on Render.
