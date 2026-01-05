<?php
/**
 * Mind Apps Analytics Endpoint
 *
 * Receives download analytics data from the Mind Apps application.
 *
 * Expected POST parameters:
 * - secret_key: Authentication key
 * - package_name: The app's package name
 * - app_name: The app's display name
 * - action: The action type (e.g., "download")
 * - timestamp: Unix timestamp of the action
 */

// Configuration
define('SECRET_KEY', 'your_secret_key_here'); // Change this to match your .env SECRET_KEY
define('LOG_FILE', __DIR__ . '/downloads.json');

// Set headers
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST');
header('Access-Control-Allow-Headers: Content-Type');

// Handle preflight requests
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

// Only accept POST requests
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'Method not allowed']);
    exit();
}

// Get POST data
$secretKey = $_POST['secret_key'] ?? '';
$packageName = $_POST['package_name'] ?? '';
$appName = $_POST['app_name'] ?? '';
$action = $_POST['action'] ?? '';
$timestamp = $_POST['timestamp'] ?? time();

// Validate secret key
if ($secretKey !== SECRET_KEY) {
    http_response_code(401);
    echo json_encode(['error' => 'Unauthorized']);
    exit();
}

// Validate required fields
if (empty($packageName) || empty($appName) || empty($action)) {
    http_response_code(400);
    echo json_encode(['error' => 'Missing required fields']);
    exit();
}

// Create analytics entry
$entry = [
    'package_name' => $packageName,
    'app_name' => $appName,
    'action' => $action,
    'timestamp' => $timestamp,
    'date' => date('Y-m-d H:i:s', (int)($timestamp / 1000)), // Convert from milliseconds
    'ip' => $_SERVER['REMOTE_ADDR'] ?? 'unknown',
    'user_agent' => $_SERVER['HTTP_USER_AGENT'] ?? 'unknown'
];

// Load existing data
$data = [];
if (file_exists(LOG_FILE)) {
    $content = file_get_contents(LOG_FILE);
    $data = json_decode($content, true) ?? [];
}

// Add new entry
$data[] = $entry;

// Save data
if (file_put_contents(LOG_FILE, json_encode($data, JSON_PRETTY_PRINT))) {
    // Update download counts
    updateDownloadCounts($packageName);

    http_response_code(200);
    echo json_encode([
        'success' => true,
        'message' => 'Analytics recorded'
    ]);
} else {
    http_response_code(500);
    echo json_encode(['error' => 'Failed to save data']);
}

/**
 * Update download counts per app
 */
function updateDownloadCounts($packageName) {
    $countsFile = __DIR__ . '/download_counts.json';

    $counts = [];
    if (file_exists($countsFile)) {
        $content = file_get_contents($countsFile);
        $counts = json_decode($content, true) ?? [];
    }

    if (!isset($counts[$packageName])) {
        $counts[$packageName] = 0;
    }
    $counts[$packageName]++;

    file_put_contents($countsFile, json_encode($counts, JSON_PRETTY_PRINT));
}

/**
 * Helper function to get download stats (can be called separately)
 * Usage: data.php?action=stats&secret_key=your_key
 */
if ($_SERVER['REQUEST_METHOD'] === 'GET' && ($_GET['action'] ?? '') === 'stats') {
    $secretKey = $_GET['secret_key'] ?? '';

    if ($secretKey !== SECRET_KEY) {
        http_response_code(401);
        echo json_encode(['error' => 'Unauthorized']);
        exit();
    }

    $countsFile = __DIR__ . '/download_counts.json';
    $counts = [];
    if (file_exists($countsFile)) {
        $content = file_get_contents($countsFile);
        $counts = json_decode($content, true) ?? [];
    }

    echo json_encode([
        'success' => true,
        'download_counts' => $counts,
        'total_downloads' => array_sum($counts)
    ]);
    exit();
}
?>
