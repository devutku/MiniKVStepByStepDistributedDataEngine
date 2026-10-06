import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '10s', target: 20 },
        { duration: '30s', target: 50 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        // 500 hatalarını (gerçek sunucu çökmelerini) takip etmek için özel kural
        'http_req_failed{status_not_404:true}': ['rate<0.01'],
        http_req_duration: ['p(95)<50', 'p(99)<100'],
    },
};

const BASE_URL = 'http://127.0.0.1:8080/kv';

export default function () {
    const randomId = Math.floor(Math.random() * 1000) + 1;
    const key = `user_${randomId}`;
    const isWrite = Math.random() < 0.2;

    if (isWrite) {
        const payload = `data_payload_v_${Date.now()}`;
        const params = {
            headers: { 'Content-Type': 'text/plain' },
        };

        const res = http.post(`${BASE_URL}/${key}`, payload, params);

        check(res, {
            'POST status 201': (r) => r.status === 201,
        });
    } else {
        // responseCallback ile 404'leri failed saymasını engelliyoruz
        const res = http.get(`${BASE_URL}/${key}`, {
            responseCallback: http.expectedStatuses(200, 404),
        });

        check(res, {
            'GET status 200 or 404': (r) => r.status === 200 || r.status === 404,
        });
    }

    sleep(0.05);
}