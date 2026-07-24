document.addEventListener('DOMContentLoaded', () => {
    const loginForm = document.getElementById('loginForm');
    const resultBox = document.getElementById('resultBox');
    const spinner = document.getElementById('loginSpinner');
    const btnText = document.querySelector('.btn-text');

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const username = document.getElementById('username').value;
        const password = document.getElementById('password').value;

        // UI Loading state
        btnText.textContent = 'Processing...';
        spinner.classList.remove('hidden');
        resultBox.innerHTML = '<span class="placeholder-text">Sending request to API Gateway...</span>';

        try {
            // In a real Datadog RUM setup, the Datadog Browser SDK would automatically 
            // inject traceparent/b3 headers here.
            // Since we are mocking simple behavior, the API Gateway will generate the root span
            // if no traceparent is provided.
            const response = await fetch('/api/v1/auth/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    // Simulated frontend generated trace ID could go here:
                    // 'traceparent': '00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01'
                },
                body: JSON.stringify({ username, password })
            });

            const data = await response.json();
            
            if (response.ok) {
                resultBox.innerHTML = `[SUCCESS] Status: ${response.status}<br><br>${JSON.stringify(data, null, 2)}`;
                resultBox.className = 'terminal-box';
            } else {
                resultBox.innerHTML = `<span class="text-error">[ERROR] Status: ${response.status}<br><br>${JSON.stringify(data, null, 2)}</span>`;
            }
        } catch (err) {
            resultBox.innerHTML = `<span class="text-error">[NETWORK ERROR] ${err.message}</span>`;
        } finally {
            // Reset UI
            btnText.textContent = 'Execute Login Flow';
            spinner.classList.add('hidden');
        }
    });
});
