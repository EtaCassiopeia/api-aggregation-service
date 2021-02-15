import http from "k6/http";

export let options = {
  stages: [
    { duration: '5m', target: 100 }, // simulate ramp-up of traffic from 1 to 100 users over 5 minutes.
    { duration: '5m', target: 1000 }, // simulate ramp-up of traffic from 1 to 1000 users over 5 minutes.
    { duration: '10m', target: 100 }, // stay at 100 users for 10 minutes
    { duration: '10m', target: 1000 }, // stay at 1000 users for 10 minutes
    { duration: '5m', target: 0 }, // ramp-down to 0 users
  ],
  thresholds: {
    http_req_duration: ['p(99)<10000'] // 99% of requests must complete below 10s  }
  },
  batchPerHost: 5
};

export default function() {
    let response = http.get("http://localhost:8080/aggregation?pricing=CN&track=109347263&shipments=109347263");
};
